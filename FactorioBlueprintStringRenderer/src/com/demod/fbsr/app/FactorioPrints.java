package com.demod.fbsr.app;

import com.demod.dcba.CommandReporting;
import com.demod.fbsr.BlueprintFinder;
import com.demod.fbsr.FBSR;
import com.demod.fbsr.bs.BSBlueprint;
import com.demod.fbsr.bs.BSBlueprintBook;
import com.demod.fbsr.bs.BSBlueprintString;
import com.demod.fbsr.gui.layout.GUILayoutBlueprint;
import com.demod.fbsr.gui.layout.GUILayoutBook;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class FactorioPrints {
    private static final boolean CONFIG_OPEN_RENDERED_FILE = false;

    private static final List<String> BLUEPRINT_IDS = List.of(
            // https://factorioprints.com/view/-OFi57En6z9z4xhUxJ8-
            // https://factorioprints.com/view/-OGkxLXOl2BamfwyMAnE
            // https://factorioprints.com/view/-OFo0h6eKudNzNzkh9zL
            // https://factorioprints.com/view/-OFL3xj6ttXqQPNLaGBY
            // https://factorioprints.com/view/-OFLE8CAd9PjPJ60YngA
            // https://factorioprints.com/view/-OGNYaoHiPUB0siObxMl
            // https://factorioprints.com/view/-OFo0h6eKudNzNzkh9zL
            // https://factorioprints.com/view/-OAJMNxgS-l_1KrHnlpY
            // https://factorioprints.com/view/-OBdjOkCw-nKJkn4XTmk
            // https://factorioprints.com/view/-OBA2tMoLvqy-lhAwqoB
            "-OFi57En6z9z4xhUxJ8-",
            "-OGkxLXOl2BamfwyMAnE",
            "-OFo0h6eKudNzNzkh9zL",
            "-OFL3xj6ttXqQPNLaGBY",
            "-OFLE8CAd9PjPJ60YngA",
            "-OGNYaoHiPUB0siObxMl",
            "-OFo0h6eKudNzNzkh9zL",
            "-OAJMNxgS-l_1KrHnlpY",
            "-OBdjOkCw-nKJkn4XTmk",
            "-OBA2tMoLvqy-lhAwqoB"
    );

    public static void main(String[] args) throws IOException {
        // Initialize Render Engine
        FBSR.initialize();

        for (String blueprintId : BLUEPRINT_IDS) {
            // Construct the URL using the blueprint id.
            String urlStr = "https://facorio-blueprints.firebaseio.com/blueprints/" + blueprintId + "/blueprintString.json";

            // Fetch the blueprint string from the URL.
            String blueprintStringRaw = fetchBlueprintString(urlStr);

            processBlueprintStringRaw(blueprintStringRaw, Paths.get(blueprintId));

            System.out.println("Finished processing url: " + urlStr);
        }
    }

    public static void processBlueprintStringRaw(String blueprintStringRaw, Path path) throws IOException {
        // Placeholder for discord reporting
        CommandReporting reporting = new CommandReporting(null, null, null);

        // Grab the first blueprint in the first blueprint string
        List<BSBlueprintString> blueprintStrings = BlueprintFinder.search(blueprintStringRaw, reporting);

        if (blueprintStrings.isEmpty())
        {
            return;
        }
        BSBlueprintString blueprintString = blueprintStrings.get(0);

        if (blueprintString.blueprintBook.isPresent()) {
            BSBlueprintBook blueprintBook = blueprintString.blueprintBook.get();
            GUILayoutBook bookLayout = new GUILayoutBook();
            bookLayout.setBook(blueprintBook);
            bookLayout.setReporting(reporting);
            BufferedImage bookImage = bookLayout.generateDiscordImage();

            // Save the image
            Files.createDirectories(path);
            File file = path.resolve("book.png").toFile();
            ImageIO.write(bookImage, "PNG", file);
            if (CONFIG_OPEN_RENDERED_FILE)
            {
                // Shell out to `open ` + file
                new ProcessBuilder("open", file.getAbsolutePath()).start();
            }
        }

        recurse(blueprintString, path);

        // Extract any exceptions
        List<Exception> exceptions = reporting.getExceptions();
        exceptions.forEach(Exception::printStackTrace);
    }

    private static void recurse(BSBlueprintString blueprintString, Path path)
    {
        if (blueprintString.blueprint.isPresent())
        {
            BSBlueprint blueprint = blueprintString.blueprint.get();
            var blueprintLayout = new GUILayoutBlueprint();
            blueprintLayout.setBlueprint(blueprint);
            var reporting = new CommandReporting(null, null, null);
            blueprintLayout.setReporting(reporting);
            BufferedImage blueprintImage = blueprintLayout.generateDiscordImage();

            try
            {
                Files.createDirectories(path);
                File file = path.resolve("blueprint.png").toFile();
                ImageIO.write(blueprintImage, "PNG", file);
                if (CONFIG_OPEN_RENDERED_FILE)
                {
                    // Shell out to `open ` + file
                    new ProcessBuilder("open", file.getAbsolutePath()).start();
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        if (blueprintString.blueprintBook.isPresent())
        {
            BSBlueprintBook blueprintBook = blueprintString.blueprintBook.get();
            List<BSBlueprintString> blueprints = blueprintBook.blueprints;
            for (int i = 0; i < blueprints.size(); i++)
            {
                BSBlueprintString child = blueprints.get(i);
                recurse(child, path.resolve(String.valueOf(i + 1)));
            }
        }
    }

    /**
     * Fetches the blueprint string from the given URL.
     * The returned value is a JSON string (surrounded by quotes) so we remove them.
     *
     * @param urlStr the URL to fetch from
     * @return the unquoted blueprint string
     * @throws IOException if the request fails
     */
    private static String fetchBlueprintString(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to fetch blueprint string from " + urlStr + ", response code: " + responseCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        String result = response.toString();
        // Remove the surrounding quotes if they exist.
        if (result.length() >= 2 && result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        } else {
            throw new IOException("Invalid blueprint string: " + result);
        }
        return result;
    }
}