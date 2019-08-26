/*
 *    Copyright 2017-2019 Frederic Thevenet
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package eu.binjr.core.dialogs;

import eu.binjr.core.controllers.OutputConsoleController;
import eu.binjr.core.preferences.AppEnvironment;
import eu.binjr.core.appearance.StageAppearanceManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * The {@link Stage} for the output console window.
 */
public class ConsoleStage {
    private static final Logger logger = LogManager.getLogger(ConsoleStage.class);
    private final Stage stage;

    private static class ConsoleStageHolder {
        private final static ConsoleStage instance = new ConsoleStage();
    }

    private ConsoleStage() {
        FXMLLoader loader = new FXMLLoader(OutputConsoleController.class.getResource("/eu/binjr/views/OutputConsoleView.fxml"));
        Parent root = null;
        try {
            root = loader.load();
        } catch (IOException e) {
            logger.error("Failed to show console windows", e.getMessage());
            logger.debug(() -> "Exception stack", e);
        }
        OutputConsoleController controller = loader.getController();
        final Scene scene = new Scene(root);
        stage = new Stage();
        stage.setScene(scene);
        stage.setTitle("binjr debug console");
        StageAppearanceManager.getInstance().register(stage);
        stage.initStyle(StageStyle.UTILITY);
        stage.show();
        stage.setOnCloseRequest(event -> {
            AppEnvironment.getInstance().setDebugMode(false);
            event.consume();
        });

        stage.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            if (e.getCode() == KeyCode.F12) {
                AppEnvironment.getInstance().setDebugMode(!AppEnvironment.getInstance().isDebugMode());
            }
        });
        controller.getAlwaysOnTopToggle().selectedProperty().addListener((observable, oldValue, newValue) -> {
            stage.setAlwaysOnTop(newValue);
        });
        controller.getAlwaysOnTopToggle().setSelected(true);
    }

    /**
     * Show the output console window.
     */
    public static void show() {
        ConsoleStageHolder.instance.stage.show();
    }

    /**
     * Hide the output console window.
     */
    public static void hide() {
        ConsoleStageHolder.instance.stage.hide();
    }

}
