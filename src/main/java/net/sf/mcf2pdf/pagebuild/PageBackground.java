/*******************************************************************************
 * ${licenseText}
 *******************************************************************************/
package net.sf.mcf2pdf.pagebuild;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.mcf2pdf.mcfelements.McfBackground;
import net.sf.mcf2pdf.mcfelements.util.ImageUtil;
import net.sf.mcf2pdf.mcfglobals.McfAlbumType;

public class PageBackground implements PageDrawable {

	private List<? extends McfBackground> leftBg;

	private List<? extends McfBackground> rightBg;

	public PageBackground(List<? extends McfBackground> leftBg, List<? extends McfBackground> rightBg) {
		this.leftBg = leftBg;
		this.rightBg = rightBg;
	}

	@Override
	public boolean isVectorGraphic() {
		return false;
	}

	@Override
	public void renderAsSvgElement(Writer writer, PageRenderContext context) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public BufferedImage renderAsBitmap(PageRenderContext context, Point drawOffsetPixels) throws IOException {
		File fLeft = extractBackground(leftBg, context);
		File fRight = extractBackground(rightBg, context);

		McfAlbumType albumType = context.getAlbumType();

		float widthMM = (albumType.getUsableWidth() + albumType.getBleedMargin()) / 10.0f * 2;
		float heightMM = (albumType.getUsableHeight() + albumType.getBleedMargin() * 2) / 10.0f;

		BufferedImage img = new BufferedImage(context.toPixel(widthMM), context.toPixel(heightMM),
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = img.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

		if (fLeft != null && fLeft.equals(fRight)) {
			// draw the background image on whole page
			drawBackground(fLeft, g2d, 0, 0, img.getWidth(), img.getHeight());
		} else {
			// process background parts separate
			if (fLeft != null)
				drawBackground(fLeft, g2d, 0, 0, img.getWidth() / 2, img.getHeight());
			if (fRight != null)
				drawBackground(fRight, g2d, img.getWidth() / 2, 0, img.getWidth() / 2, img.getHeight());
		}

		g2d.dispose();
		return img;
	}

	private void drawBackground(File f, Graphics2D g2d, int x, int y, int width, int height) throws IOException {
		BufferedImage img = ImageUtil.readImage(f);
		if (img == null) {
			throw new IOException("Could not read image file: " + f.getAbsolutePath());
		}

		float tgtRatio = width / (float) height;

		float imgRatio = img.getWidth() / (float) img.getHeight();
		float scale;
		boolean xVar;

		if (imgRatio > tgtRatio) {
			// scale image Y to target Y
			scale = height / (float) img.getHeight();
			xVar = true;
		} else {
			// scale image X to target X
			scale = width / (float) img.getWidth();
			xVar = false;
		}

		int sx = (int) (xVar ? ((img.getWidth() - (width / scale)) / 2) : 0);
		int sy = (int) (xVar ? 0 : ((img.getHeight() - (height / scale)) / 2));

		int sw = (int) (width / scale);
		int sh = (int) (height / scale);

		g2d.drawImage(img, x, y, x + width, y + height, sx, sy, sx + sw, sy + sh, null);
	}

	private File extractBackground(List<? extends McfBackground> bgs, PageRenderContext context) throws IOException {
		for (McfBackground bg : bgs) {
			String tn = bg.getTemplateName();

			// enhanced pattern to cover the examples with hue and fading set
			Pattern pattern = Pattern.compile("([a-zA-Z0-9_]+)(,hue=([0-9]+))?(,fading=([0-9]+))?,normal(,.*)?");
			Matcher matcher = pattern.matcher(tn);
			if (tn == null || !matcher.find())
				continue;
			boolean multicolorBg = false;
			int hue = 0;
			int fading = 0;
			
			// extract the parameters
			if (matcher.groupCount() == 6) {
				tn = matcher.group(1);
				if (matcher.group(3) != null) {
					hue = Integer.parseInt(matcher.group(3));
					multicolorBg = true;
				}
				if (matcher.group(5) != null) {
					fading = Integer.parseInt(matcher.group(5));
					multicolorBg = true;
				}
				if (multicolorBg)
					context.getLog().debug("Multicolor background for page no. " + bg.getPage().getPageNr()
							+ " parameters are: hue=" + hue + ", fading=" + fading);
			}

			if (multicolorBg) {
				tn = tn + "_" + hue + "_" + fading + ".png";
			}
			File f = context.getBackgroundImage(tn);
			if (f == null) {
				f = context.getBackgroundColor(tn);
			}
			if (f == null)
				context.getLog().warn("Background not found for page " + bg.getPage().getPageNr() + ": " + tn);
			else
				return f;
		}

		return null;
	}

	@Override
	public int getZPosition() {
		return 0;
	}

	@Override
	public float getLeftMM() {
		return 0;
	}

	@Override
	public float getTopMM() {
		return 0;
	}

}
