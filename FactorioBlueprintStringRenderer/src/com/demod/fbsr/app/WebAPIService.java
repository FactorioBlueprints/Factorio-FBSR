package com.demod.fbsr.app;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.rapidoid.http.MediaType;
import org.rapidoid.setup.App;
import org.rapidoid.setup.On;

import com.demod.factorio.Config;
import com.demod.factorio.Utils;
import com.demod.fbsr.Blueprint;
import com.demod.fbsr.BlueprintFinder;
import com.demod.fbsr.BlueprintStringData;
import com.demod.fbsr.FBSR;
import com.demod.fbsr.TaskReporting;
import com.demod.fbsr.WebUtils;
import com.google.common.util.concurrent.AbstractIdleService;

public class WebAPIService extends AbstractIdleService {

	private JsonNode configJson;

	private String saveToLocalStorage(File folder, BufferedImage image) throws IOException {
		if (!folder.exists()) {
			folder.mkdirs();
		}

		File imageFile;
		long id = System.currentTimeMillis();
		String fileName;
		while ((imageFile = new File(folder, fileName = "Blueprint" + id + ".png")).exists()) {
			id++;
		}

		ImageIO.write(image, "PNG", imageFile);

		return fileName;
	}

	@Override
	protected void shutDown() throws Exception {
		ServiceFinder.removeService(this);

		App.shutdown();
	}

	@Override
	protected void startUp() throws Exception {
		ServiceFinder.addService(this);

		configJson = Config.get().path("webapi");

		String address = configJson.path("bind").asText("0.0.0.0");
		int port = configJson.path("port").asInt(80);

		On.address(address).port(port);

		On.post("/blueprint").serve((req, resp) -> {
			System.out.println("Web API POST!");
			TaskReporting reporting = new TaskReporting();
			try {
				ObjectMapper objectMapper = new ObjectMapper();
				ObjectWriter objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
				try {
					byte[] requestBody = req.body();
					if (requestBody == null) {
						resp.code(400);
						resp.plain("Body is empty!");
						reporting.addException(new IllegalArgumentException("Body is empty!"));
						return resp;
					}

					JsonNode bodyNode;
					try {
						String requestBodyString = new String(requestBody);
						bodyNode = objectMapper.readTree(requestBodyString);
					} catch (Exception e) {
						reporting.addException(e);
						resp.code(400);
						resp.plain("Malformed JSON: " + e.getMessage());
						return resp;
					}
					String string = objectWriter.writeValueAsString(bodyNode);
					reporting.setContext(string);

					/*
					 * 	{
					 * 		"blueprint": "0e...", 		(required)
					 * 		"max-width": 1234,
					 * 		"max-height": 1234,
					 * 		"show-info-panels": false
					 * 	}
					 * 				|
					 * 				v
					 * {
					 * 		"info": [
					 * 			"message 1!", "message 2!", ...
					 * 		],
					 * 		"images": [
					 * 			{
					 * 				"label": "Blueprint Label",
					 * 				"link": "https://cdn.discordapp.com/..." (or) "1563569893008.png"
					 * 			}
					 * 		]
					 * }
					 */

					String content = bodyNode.path("blueprint").textValue();

					List<BlueprintStringData> blueprintStrings = BlueprintFinder.search(content, reporting);
					List<Blueprint> blueprints = blueprintStrings.stream().flatMap(s -> s.getBlueprints().stream())
							.collect(Collectors.toList());

					for (Blueprint blueprint : blueprints) {
						try {
							BufferedImage image = FBSR.renderBlueprint(blueprint, reporting, bodyNode);
							if (configJson.path("use-local-storage").asBoolean(false)) {
								JsonNode localStorage = configJson.path("local-storage");
								assert localStorage.isTextual();
								File localStorageFolder = new File(localStorage.textValue());
								String imageLink = saveToLocalStorage(localStorageFolder, image);
								reporting.addImage(blueprint.getLabel(), imageLink);
								reporting.addLink(imageLink);
							} else {
								reporting.addImage(blueprint.getLabel(),
										WebUtils.uploadToHostingService("blueprint.png", image).toString());
							}
						} catch (Exception e) {
							reporting.addException(e);
						}
					}
				} catch (Exception e) {
					reporting.addException(e);
				}

				ObjectNode result = objectMapper.createObjectNode();

				if (!reporting.getExceptions().isEmpty()) {
					reporting.addInfo(
							"There was a problem completing your request. I have contacted my programmer to fix it for you!");
				}

				if (!reporting.getInfo().isEmpty()) {
					ArrayNode info = result.putArray("info");
					reporting.getInfo().forEach(info::add);
				}

				if (!reporting.getImages().isEmpty()) {
					ArrayNode images = result.putArray("images");
					for (Entry<Optional<String>, String> pair : reporting.getImages()) {
						ObjectNode image = images.addObject();
						pair.getKey().ifPresent(l -> image.put("label", l));
						image.put("link", pair.getValue());
					}
				}

				String string = objectWriter.writeValueAsString(result);
				resp.contentType(MediaType.JSON);
				resp.body(string.getBytes());

				return resp;

			} finally {
				ServiceFinder.findService(BlueprintBotDiscordService.class)
						.ifPresent(s -> s.sendReport(
								"Web API / " + req.clientIpAddress() + " / "
										+ Optional.ofNullable(req.header("User-Agent", null)).orElse("<Unknown>"),
								null, reporting));
			}

		});

		System.out.println("Web API Initialized at " + address + ":" + port);
	}

}
