package planner;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) {
        Path path = Path.of(args.length > 0 ? args[0] : "catalog.json");
        Catalog catalog;
        try {
            catalog = Catalog.load(path);
        } catch (Exception e) {
            System.err.println("Could not load " + path + ": " + e.getMessage());
            System.exit(1);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // fall back to the default look and feel
            }
            new PlannerFrame(catalog).setVisible(true);
        });
    }
}
