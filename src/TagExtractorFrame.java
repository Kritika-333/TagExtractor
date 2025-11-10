import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class TagExtractorFrame extends JFrame {
    private final JLabel fileLabel = new JLabel("No file selected");
    private final JLabel stopWordsLabel = new JLabel("No stopwords file selected");
    private final JTextArea outputArea = new JTextArea(25, 70);
    private final JButton chooseFileBtn = new JButton("Choose Text File...");
    private final JButton chooseStopBtn = new JButton("Choose Stop Words...");
    private final JButton extractBtn = new JButton("Extract Tags");
    private final JButton saveBtn = new JButton("Save Tags...");
    private final JButton clearBtn = new JButton("Clear");
    private final JFileChooser fileChooser = new JFileChooser();
    private final JFileChooser saveChooser = new JFileChooser();
    private Path textFile = null;
    private Path stopFile = null;
    private Set<String> stopWords = new TreeSet<>();
    private Map<String, Integer> freqMap = new TreeMap<>();

    public TagExtractorFrame() {
        super("Tag / Keyword Extractor");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setLocationRelativeTo(null);

        // Top panel: file selectors
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST;
        top.add(new JLabel("Text file:"), c);
        c.gridx = 1; top.add(fileLabel, c);
        c.gridx = 2; top.add(chooseFileBtn, c);

        c.gridx = 0; c.gridy = 1; top.add(new JLabel("Stop words file:"), c);
        c.gridx = 1; top.add(stopWordsLabel, c);
        c.gridx = 2; top.add(chooseStopBtn, c);

        add(top, BorderLayout.NORTH);

        // Center: output area
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(outputArea);
        add(scroll, BorderLayout.CENTER);

        // Bottom: action buttons
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(extractBtn);
        bottom.add(saveBtn);
        bottom.add(clearBtn);
        add(bottom, BorderLayout.SOUTH);

        // Setup file choosers
        fileChooser.setFileFilter(new FileNameExtensionFilter("Text files", "txt", "text"));
        saveChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        // Button behaviors
        chooseFileBtn.addActionListener(e -> chooseTextFile());
        chooseStopBtn.addActionListener(e -> chooseStopFile());
        extractBtn.addActionListener(e -> extractTags());
        saveBtn.addActionListener(e -> saveTags());
        clearBtn.addActionListener(e -> clearAll());

        // Initial state
        saveBtn.setEnabled(false);
        pack();
    }

    private void chooseTextFile() {
        int res = fileChooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            textFile = fileChooser.getSelectedFile().toPath();
            fileLabel.setText(textFile.getFileName().toString());
            outputArea.append("Selected text file: " + textFile + "\n");
        }
    }

    private void chooseStopFile() {
        int res = fileChooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            stopFile = fileChooser.getSelectedFile().toPath();
            stopWordsLabel.setText(stopFile.getFileName().toString());
            outputArea.append("Selected stop words file: " + stopFile + "\n");
            try {
                loadStopWords(stopFile);
                outputArea.append("Loaded " + stopWords.size() + " stop words.\n");
            } catch (IOException ex) {
                showError("Error loading stop words: " + ex.getMessage());
            }
        }
    }

    private void loadStopWords(Path path) throws IOException {
        stopWords.clear();
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip().toLowerCase();
                if (!line.isEmpty()) stopWords.add(line);
            }
        }
    }

    private void extractTags() {
        if (textFile == null) { showError("Pick a text file first."); return; }
        if (stopFile == null) { showError("Pick a stop words file (or use the provided English Stop Words)."); return; }

        freqMap.clear();
        outputArea.append("\nScanning file: " + textFile + "\n");

        try (BufferedReader br = Files.newBufferedReader(textFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                // remove non-letter characters -> space; convert to lower case
                // Keep only letters: replace other with space
                String cleaned = line.replaceAll("[^A-Za-z]", " ").toLowerCase();
                String[] tokens = cleaned.split("\\s+");
                for (String t : tokens) {
                    if (t == null || t.isBlank()) continue;
                    String w = t.strip();
                    if (w.length() == 0) continue;
                    if (stopWords.contains(w)) continue; // filter stop words
                    // increment map count
                    freqMap.merge(w, 1, Integer::sum);
                }
            }
        } catch (IOException ex) {
            showError("Error reading text file: " + ex.getMessage());
            return;
        }

        if (freqMap.isEmpty()) {
            outputArea.append("No tags found (check stop words or file content).\n");
            saveBtn.setEnabled(false);
            return;
        }

        // Prepare sorted list by frequency descending then word ascending
        List<Map.Entry<String,Integer>> sorted = freqMap.entrySet()
                .stream()
                .sorted((e1,e2) -> {
                    int cmp = Integer.compare(e2.getValue(), e1.getValue()); // descending frequency
                    return cmp != 0 ? cmp : e1.getKey().compareTo(e2.getKey());
                })
                .collect(Collectors.toList());

        outputArea.append("\nTags (word : frequency)\n");
        outputArea.append("=========================\n");
        for (Map.Entry<String,Integer> e : sorted) {
            outputArea.append(String.format("%-20s : %d%n", e.getKey(), e.getValue()));
        }
        outputArea.append("\nTotal distinct tags: " + freqMap.size() + "\n");
        saveBtn.setEnabled(true);
    }

    private void saveTags() {
        if (freqMap.isEmpty()) { showError("Nothing to save. Run extraction first."); return; }
        int res = saveChooser.showSaveDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;
        Path out = saveChooser.getSelectedFile().toPath();
        // Ensure extension .txt
        if (!out.toString().toLowerCase().endsWith(".txt")) {
            out = out.resolveSibling(out.getFileName() + ".txt");
        }
        try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            // Write header
            bw.write("Tags for file: " + (textFile == null ? "unknown" : textFile.getFileName().toString()));
            bw.newLine();
            bw.write("=========================");
            bw.newLine();
            // write sorted by frequency
            freqMap.entrySet().stream()
                    .sorted((e1,e2) -> {
                        int cmp = Integer.compare(e2.getValue(), e1.getValue());
                        return cmp != 0 ? cmp : e1.getKey().compareTo(e2.getKey());
                    })
                    .forEach(e -> {
                        try {
                            bw.write(String.format("%s : %d", e.getKey(), e.getValue()));
                            bw.newLine();
                        } catch (IOException ex) {
                            // bubble up
                        }
                    });
            bw.flush();
            JOptionPane.showMessageDialog(this, "Tags saved to: " + out.toAbsolutePath());
        } catch (IOException ex) {
            showError("Error saving file: " + ex.getMessage());
        }
    }

    private void clearAll() {
        outputArea.setText("");
        fileLabel.setText("No file selected");
        stopWordsLabel.setText("No stopwords file selected");
        textFile = null;
        stopFile = null;
        stopWords.clear();
        freqMap.clear();
        saveBtn.setEnabled(false);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
