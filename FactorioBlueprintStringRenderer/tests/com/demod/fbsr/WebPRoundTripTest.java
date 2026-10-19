package com.demod.fbsr;

import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.Test;

import com.luciad.imageio.webp.WebPWriteParam;

/**
 * Atlases are stored as WebP, so the WebP codec must load on every platform the asset build runs
 * on, Apple Silicon included.
 */
public class WebPRoundTripTest {

	@Test
	public void aWebPImageReadsBackWithItsPixels() throws IOException {
		BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(1, 2, 0xFF336699);

		ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
		WebPWriteParam param = new WebPWriteParam(writer.getLocale());
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionType(param.getCompressionTypes()[WebPWriteParam.LOSSLESS_COMPRESSION]);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(bytes)) {
			writer.setOutput(ios);
			writer.write(null, new IIOImage(image, null, null), param);
		} finally {
			writer.dispose();
		}

		BufferedImage read = ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));

		assertEquals(0xFF336699, read.getRGB(1, 2));
	}
}
