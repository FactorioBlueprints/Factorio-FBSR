package com.demod.fbsr;

import static org.junit.Assert.assertEquals;

import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageWriterSpi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.luciad.imageio.webp.WebPImageReaderSpi;
import com.luciad.imageio.webp.WebPImageWriterSpi;

/**
 * ImageIO finds the WebP codec through service files on the classpath, which a repackaged jar or a
 * separate class loader can hide. Atlases must still read and write when that discovery misses.
 */
public class WebPRegistrationTest {

	private final IIORegistry registry = IIORegistry.getDefaultInstance();

	@Before
	public void hideTheAutoRegisteredCodec() {
		registry.deregisterServiceProvider(registry.getServiceProviderByClass(WebPImageWriterSpi.class),
				ImageWriterSpi.class);
		registry.deregisterServiceProvider(registry.getServiceProviderByClass(WebPImageReaderSpi.class),
				ImageReaderSpi.class);
	}

	@After
	public void restoreTheCodec() {
		registry.registerServiceProvider(new WebPImageWriterSpi());
		registry.registerServiceProvider(new WebPImageReaderSpi());
	}

	@Test
	public void theWriterRegistersWhenDiscoveryMissedIt() {
		ImageWriter writer = AtlasPackage.webPWriter();

		assertEquals(WebPImageWriterSpi.class, writer.getOriginatingProvider().getClass());
	}

	@Test
	public void theReaderRegistersWhenDiscoveryMissedIt() {
		AtlasPackage.registerWebPReader();

		List<ImageReader> readers = ImmutableList.copyOf(ImageIO.getImageReadersByMIMEType("image/webp"));
		assertEquals(List.of(WebPImageReaderSpi.class),
				readers.stream().map(reader -> reader.getOriginatingProvider().getClass()).toList());
	}
}
