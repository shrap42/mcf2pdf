/*******************************************************************************
 * ${licenseText}
 *******************************************************************************/
package net.sf.mcf2pdf.mcfglobals;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageProducer;
import java.awt.image.RGBImageFilter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.digester3.Digester;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.mcf2pdf.mcfconfig.Decoration;
import net.sf.mcf2pdf.mcfconfig.Fading;
import net.sf.mcf2pdf.mcfconfig.Template;
import net.sf.mcf2pdf.mcfelements.impl.DigesterConfiguratorImpl;
import net.sf.mcf2pdf.mcfelements.util.ImageUtil;

/**
 * "Dirty little helper" which scans installation directory and temporary
 * directory of fotobook software for background images, cliparts, fonts, and
 * masks (fadings). As there is no (known) usable TOC for these, we just take
 * what we find.
 */
public class McfResourceScanner {

	private final static Log log = LogFactory.getLog(McfResourceScanner.class);

	private List<File> scanDirs = new ArrayList<File>();

	private Map<String, File> foundImages = new HashMap<String, File>();

	private Map<String, File> foundClips = new HashMap<String, File>();

	private Map<String, Font> foundFonts = new HashMap<String, Font>();

	private Map<String, File> foundColors = new HashMap<String, File>();

	private Map<String, Fading> foundDecorations = new HashMap<String, Fading>();

	private File foundBinding;

	private int hue;
	private int fading;

	public McfResourceScanner(List<File> scanDirs) {
		this.scanDirs.addAll(scanDirs);
	}

	public void scan() throws IOException {
		for (File f : scanDirs) {
			scanDirectory(f);
		}
	}

	private void scanDirectory(File dir) throws IOException {
		if (!dir.isDirectory())
			return;

		for (File f : dir.listFiles()) {
			if (f.isDirectory())
				scanDirectory(f);
			else {
				String nm = f.getName().toLowerCase(Locale.US);
				String path = f.getAbsolutePath();
				if (nm.matches(".+\\.(jp(e?)g|webp|bmp)")) {
					String id = nm.substring(0, nm.indexOf("."));
					foundImages.put(id, f);
				} else if (nm.matches(".+\\.(clp|svg)")) {
					String id = f.getName().substring(0, nm.lastIndexOf("."));
					foundClips.put(id, f);
				} else if (nm.equals("1_color_backgrounds.xml")) {
					log.debug("Processing 1-color backgrounds " + f.getAbsolutePath());
					List<Template> colors = loadColorsMapping(f);
					for (Template color : colors) {
						File colorFile = new File(f.getParent() + '/' + color.getFilename());
						foundColors.put(color.getName(), colorFile);
					}
				} else if (nm.matches(".+\\.ttf")) {
					Font font = loadFont(f);
					foundFonts.put(font.getFamily(), font);
				} else if (nm.matches("normalbinding.*\\.png")) {
					foundBinding = f;
				} else if (nm.matches(".+\\.xml")
						&& (path.contains("/decorations/") || (path.contains("\\decorations\\")))) {
					String id = f.getName().substring(0, nm.lastIndexOf("."));
					List<Decoration> spec = loadDecoration(f);
					if (spec.size() == 1) {
						foundDecorations.put(id, spec.get(0).getFading());
					} else {
						log.warn("Failed to load decorations from: " + path);
					}
				}
			}
		}
	}

	private static Font loadFont(File f) throws IOException {
		FileInputStream is = new FileInputStream(f);
		try {
			return Font.createFont(Font.TRUETYPE_FONT, is);
		} catch (FontFormatException e) {
			throw new IOException(e);
		} finally {
			IOUtils.closeQuietly(is);
		}
	}

	private static List<Template> loadColorsMapping(File f) {
		Digester digester = new Digester();
		DigesterConfiguratorImpl configurator = new DigesterConfiguratorImpl();
		try {
			configurator.configureDigester(digester, f);
			return digester.parse(f);
		} catch (Exception e) {
			log.warn("Cannot parse 1-color file", e);
		}
		return Collections.emptyList();
	}

	private static List<Decoration> loadDecoration(File f) {
		Digester digester = new Digester();
		DigesterConfiguratorImpl configurator = new DigesterConfiguratorImpl();
		try {
			configurator.configureDigester(digester, f);
			return digester.parse(f);
		} catch (Exception e) {
			log.warn("Failed to load decorations", e);
		}
		return null;
	}

	public File getImage(String id) {
		return foundImages.get(id);
	}

	public File getColorImage(String name) {
		return foundColors.get(name);
	}

	public File getClip(String id) {
		return foundClips.get(id);
	}

	public File getBinding() {
		return foundBinding;
	}

	public Font getFont(String id) {
		return foundFonts.get(id);
	}

	public Fading getDecoration(String id) {
		return foundDecorations.get(id);
	}

	public void addMulticolorBackground(String templateName, File tempDir) throws IOException  {
		
		// typical templateName looks like: 357,hue=60,fading=2,normal ...
		// files with names 357.webm etc are already loaded in foundImages
		Pattern pattern = Pattern.compile("([a-zA-Z0-9_]+),hue=([0-9]+),fading=([0-9]+),normal(,.*)?");
		Matcher matcher = pattern.matcher(templateName);
		String id = "";
		if (!matcher.find())
			log.error("Error during parsing multicolor background. Template name: " + templateName);
		
		// extract the parameters
		if (matcher.groupCount() == 4) {
			id = matcher.group(1);
			if (matcher.group(2) != null) {
				hue = Integer.parseInt(matcher.group(2));
			}
			if (matcher.group(3) != null) {
				fading = Integer.parseInt(matcher.group(3));
			}
			log.debug("Multicolor background found. Parameters are: hue=" + hue + ", fading=" + fading);
		}

		// get the base file
		File f = foundImages.get(id);
		if (f != null) {
			
			// change the hue and add fading
			BufferedImage img = ImageUtil.readImage(f);
			ImageFilter filter = new RGBImageFilter() {
				@Override
				public int filterRGB(int x, int y, int rgb) {
					int alpha = (rgb & 0xff000000);
					int red = (rgb & 0xff0000) >> 16;
					int green = (rgb & 0x00ff00) >> 8;
					int blue = (rgb & 0x0000ff);
					float[] hsbvals = new float[3];
					
					// convert from RGB to HSB and apply the fading if necessary
					if (fading > 0)
						Color.RGBtoHSB(red + (255 - red) / 4 * (fading + 1),
								green + (255 - green) / 4 * (fading + 1), blue + (255 - blue) / 4 * (fading + 1),
								hsbvals);
					else
						Color.RGBtoHSB(red, green, blue, hsbvals);
					
					// apply the hue
					int rgbOut = Color.HSBtoRGB(((float) hue) / 360, hsbvals[1], hsbvals[2]);
					return alpha | rgbOut;
				}
			};
			
			// apply the filter
			ImageProducer prod = new FilteredImageSource(img.getSource(), filter);
			Image filteredImage = Toolkit.getDefaultToolkit().createImage(prod);
			
			// render the image
			BufferedImage outputImage = new BufferedImage(filteredImage.getWidth(null),
					filteredImage.getHeight(null), BufferedImage.TYPE_INT_ARGB);
			Graphics2D bGr = outputImage.createGraphics();
			bGr.drawImage(filteredImage, 0, 0, null);
			bGr.dispose();
			
			// write the image to file in the temp directory (same as pages are rendered to)
			String newFileName = id + "_" + hue + "_" + fading + ".png";
			File tempFile = new File(tempDir + "\\" + newFileName);
			ImageIO.write(outputImage, "png", tempFile);
			
			// add the image to the list of found images (with the hue and fading parameters being part of the id)
			foundImages.put(newFileName, tempFile);
		}	
	}
}
