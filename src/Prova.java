import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import java.util.HashSet;
import java.util.Set;

public class Prova extends Application {

    // Set per memorizzare i tasti attualmente premuti
    private final Set<String> tastiPremuti = new HashSet<>();

    @Override
    public void start(Stage primaryStage) {
        Pane root = new Pane();
        Circle sprite = new Circle(20, Color.BLUE);
        Circle sprite1 = new Circle(20, Color.RED);
        sprite.setTranslateX(200);
        sprite.setTranslateY(200);
        sprite1.setTranslateX(300);
        sprite1.setTranslateY(300);
        root.getChildren().addAll(sprite, sprite1);

        Scene scene = new Scene(root, 500, 500);

        // 1. Gestione Input
        scene.setOnKeyPressed(e -> tastiPremuti.add(e.getCode().toString()));
        scene.setOnKeyReleased(e -> tastiPremuti.remove(e.getCode().toString()));

        // 2. Game Loop (AnimationTimer)
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double velocita = 5.0;

                if (tastiPremuti.contains("UP"))    sprite.setTranslateY(sprite.getTranslateY() - velocita);
                if (tastiPremuti.contains("DOWN"))  sprite.setTranslateY(sprite.getTranslateY() + velocita);
                if (tastiPremuti.contains("LEFT"))  sprite.setTranslateX(sprite.getTranslateX() - velocita);
                if (tastiPremuti.contains("RIGHT")) sprite.setTranslateX(sprite.getTranslateX() + velocita);
            }
        };

        timer.start();

        primaryStage.setTitle("Movimento Fluido JavaFX");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}