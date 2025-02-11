package com.demod.fbsr.app;

import com.demod.dcba.CommandReporting;
import com.demod.fbsr.BlueprintFinder;
import com.demod.fbsr.FBSR;
import com.demod.fbsr.RenderRequest;
import com.demod.fbsr.bs.BSBlueprint;
import com.demod.fbsr.bs.BSBlueprintBook;
import com.demod.fbsr.bs.BSBlueprintString;
import com.demod.fbsr.gui.layout.GUILayoutBlueprint;
import com.demod.fbsr.gui.layout.GUILayoutBook;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

class Scratch {
    private static final List<String> BLUEPRINT_IDS = List.of(
            "-OIfV9sS1MnQLB4vhNKz");

    public static void main(String[] args) throws IOException {
        // Initialize Render Engine
        FBSR.initialize();

        // Placeholder for discord reporting
        CommandReporting reporting = new CommandReporting(null, null, null);

        // Blueprint string or url
        String blueprintStringRaw = "https://gist.github.com/demodude4u/86a3b26d7bb6f66828de8e1345cbc024";

        // Grab the first blueprint in the first blueprint string
        List<BSBlueprintString> blueprintStrings = BlueprintFinder.search(blueprintStringRaw, reporting);

        GUILayoutBlueprint blueprintLayout = new GUILayoutBlueprint();
        BSBlueprintString blueprintString = blueprintStrings.get(0);
        List<BSBlueprint> blueprints = blueprintString.findAllBlueprints();
        BSBlueprint blueprint = blueprints.get(0);
        blueprintLayout.setBlueprint(blueprint);
        blueprintLayout.setReporting(reporting);
        BufferedImage blueprintImage = blueprintLayout.generateDiscordImage();

        // Save the image
        ImageIO.write(blueprintImage, "PNG", new File("blueprint.png"));

        if (blueprintString.blueprintBook.isPresent()) {
            BSBlueprintBook blueprintBook = blueprintString.blueprintBook.get();
            GUILayoutBook bookLayout = new GUILayoutBook();
            bookLayout.setBook(blueprintBook);
            bookLayout.setReporting(reporting);
            BufferedImage bookImage = bookLayout.generateDiscordImage();

            // Save the image
            ImageIO.write(bookImage, "PNG", new File("book.png"));
        }


        // Extract any exceptions
        List<Exception> exceptions = reporting.getExceptions();
        exceptions.forEach(Exception::printStackTrace);



    }

    private static RenderRequest getRenderRequest(List<BSBlueprintString> blueprintStrings, CommandReporting reporting) {
        BSBlueprintString blueprintString = blueprintStrings.get(0);
        List<BSBlueprint> blueprints = blueprintString.findAllBlueprints();
        BSBlueprint blueprint = blueprints.get(0);

        // Request configuration
        RenderRequest renderRequest = new RenderRequest(blueprint, reporting);

        renderRequest.setBackground(Optional.of(FBSR.GROUND_COLOR));
        renderRequest.setGridLines(Optional.of(FBSR.GRID_COLOR));

        //renderRequest.setMinWidth(OptionalInt.of(1024));
        //renderRequest.setMinHeight(OptionalInt.of(1024));
        //renderRequest.setMaxWidth(OptionalInt.of(1024));
        //renderRequest.setMaxHeight(OptionalInt.of(1024));
        renderRequest.setMaxScale(OptionalDouble.of(2.0));
        return renderRequest;
    }
}