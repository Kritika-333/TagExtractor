import javax.swing.SwingUtilities;

public class TagExtractorApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TagExtractorFrame frame = new TagExtractorFrame();
            frame.setVisible(true);
        });
    }
}
