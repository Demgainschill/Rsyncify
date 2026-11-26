package com.rsyncify;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackupController {

    @FXML private TabPane tabPane;
    @FXML private StackPane setupPane;
    @FXML private StackPane progressPane;

    private TextField sourceField;
    private TextField destField;
    private RadioButton rsyncRadio;
    private RadioButton tarRadio;
    private CheckBox deleteCheck;
    private CheckBox compressCheck;
    private CheckBox dryRunCheck;
    private Slider bandwidthSlider;
    private Label bandwidthLabel;

    private ProgressBar progressBar;
    private Label percentLabel;
    private Label statusLabel;
    private TextArea logArea;

    @FXML private Button pauseBtn;
    @FXML private Button resumeBtn;
    @FXML private Button stopBtn;

    private volatile Process activeProcess;
    private volatile boolean transferPaused = false;

    private boolean initialized = false;
    private final Pattern percentPattern = Pattern.compile("(\\d+)%");

    @FXML
    private void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            FXMLLoader setupLoader = new FXMLLoader(getClass().getResource("/backup-main.fxml"));
            setupLoader.setController(this);
            Node setupNode = setupLoader.load();
            setupPane.getChildren().setAll(setupNode);

            Map<String, Object> ns = setupLoader.getNamespace();
            sourceField     = (TextField)  ns.get("sourceField");
            destField       = (TextField)  ns.get("destField");
            rsyncRadio      = (RadioButton) ns.get("rsyncRadio");
            tarRadio        = (RadioButton) ns.get("tarRadio");
            deleteCheck     = (CheckBox)   ns.get("deleteCheck");
            compressCheck   = (CheckBox)   ns.get("compressCheck");
            dryRunCheck     = (CheckBox)   ns.get("dryRunCheck");
            bandwidthSlider = (Slider)     ns.get("bandwidthSlider");
            bandwidthLabel  = (Label)      ns.get("bandwidthLabel");

            FXMLLoader progLoader = new FXMLLoader(getClass().getResource("/backup-progress.fxml"));
            progLoader.setController(this);
            Node progNode = progLoader.load();
            progressPane.getChildren().setAll(progNode);

            Map<String, Object> pns = progLoader.getNamespace();
            progressBar  = (ProgressBar) pns.get("progressBar");
            percentLabel = (Label)       pns.get("percentLabel");
            statusLabel  = (Label)       pns.get("statusLabel");
            logArea      = (TextArea)    pns.get("logArea");
            pauseBtn     = (Button)      pns.get("pauseBtn");
            resumeBtn    = (Button)      pns.get("resumeBtn");
            stopBtn      = (Button)      pns.get("stopBtn");

            wireOptionLogic();

            if (bandwidthSlider != null && bandwidthLabel != null) {
                bandwidthSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                    int kb = newVal.intValue();
                    bandwidthLabel.setText(kb == 0 ? "Unlimited" : kb + " KB/s");
                });
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void wireOptionLogic() {
        if (rsyncRadio == null || tarRadio == null) return;

        ToggleGroup group = new ToggleGroup();
        rsyncRadio.setToggleGroup(group);
        tarRadio.setToggleGroup(group);

        rsyncRadio.selectedProperty().addListener((o, was, is) -> refreshOptionAvailability());
        tarRadio.selectedProperty().addListener((o, was, is) -> refreshOptionAvailability());
        refreshOptionAvailability();
    }

    private void refreshOptionAvailability() {
        boolean rsyncMode = rsyncRadio != null && rsyncRadio.isSelected();

        if (deleteCheck != null) {
            deleteCheck.setDisable(!rsyncMode);
            if (!rsyncMode) deleteCheck.setSelected(false);
        }
        if (compressCheck != null) {
            compressCheck.setDisable(rsyncMode);
            if (rsyncMode) compressCheck.setSelected(false);
        }
    }

    @FXML
    private void browseSource() {
        DirectoryChooser chooser = new DirectoryChooser();
        File chosen = chooser.showDialog(null);
        if (chosen != null && sourceField != null) {
            sourceField.setText(chosen.getAbsolutePath());
        }
    }

    @FXML
    private void browseDest() {
        DirectoryChooser chooser = new DirectoryChooser();
        File chosen = chooser.showDialog(null);
        if (chosen != null && destField != null) {
            destField.setText(chosen.getAbsolutePath());
        }
    }

    @FXML
    private void newJob() {
        if (sourceField != null) sourceField.clear();
        if (destField != null) destField.clear();
        if (deleteCheck != null) deleteCheck.setSelected(false);
        if (compressCheck != null) compressCheck.setSelected(false);
        if (dryRunCheck != null) dryRunCheck.setSelected(false);
        if (bandwidthSlider != null) bandwidthSlider.setValue(0);
        if (rsyncRadio != null) rsyncRadio.setSelected(true);
    }

    @FXML
    private void exitApp() {
        Stage stage = (Stage) tabPane.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void viewHistory() {
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("History");
        info.setHeaderText("Past Jobs");
        info.setContentText("Job history will appear here in a future version.");
        info.showAndWait();
    }

    @FXML
    private void startBackup() {
        if (sourceField == null || destField == null) return;

        String src = sourceField.getText().trim();
        String dst = destField.getText().trim();
        if (src.isEmpty() || dst.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Please select both source and destination.").showAndWait();
            return;
        }

        boolean useRsync   = rsyncRadio != null && rsyncRadio.isSelected();
        boolean doDelete   = deleteCheck != null && deleteCheck.isSelected() && !deleteCheck.isDisabled();
        boolean doCompress = compressCheck != null && compressCheck.isSelected() && !compressCheck.isDisabled();
        boolean dryRun     = dryRunCheck != null && dryRunCheck.isSelected();
        int bandwidth      = bandwidthSlider != null ? (int) bandwidthSlider.getValue() : 0;

        if (progressBar != null) progressBar.setProgress(0);
        if (percentLabel != null) percentLabel.setText("0%");
        if (logArea != null) logArea.clear();
        if (statusLabel != null) statusLabel.setText("Starting transfer...");

        if (tabPane != null) tabPane.getSelectionModel().select(1);
        launchTransfer(src, dst, useRsync, doDelete, doCompress, dryRun, bandwidth);
    }

    @FXML
    public void backToMain() {
        if (tabPane != null) tabPane.getSelectionModel().select(0);
    }

    private void launchTransfer(String src, String dst, boolean useRsync,
                                boolean doDelete, boolean doCompress,
                                boolean dryRun, int bandwidth) {

        transferPaused = false;
        setControlState(true, false, true);

        new Thread(() -> {
            try {
                ProcessBuilder builder;
                if (useRsync) {
                    builder = new ProcessBuilder("rsync", "-avh", "--info=progress2", src + "/", dst);
                    if (doDelete) builder.command().add("--delete");
                    if (dryRun)   builder.command().add("--dry-run");
                    if (bandwidth > 0) builder.command().add("--bwlimit=" + bandwidth);
                } else {
                    String archiveName = dst + "/backup.tar" + (doCompress ? ".gz" : "");
                    builder = new ProcessBuilder("tar", "-cvf", archiveName, "-C", src, ".");
                }

                builder.redirectErrorStream(true);
                activeProcess = builder.start();

                long lastProgressUpdate = 0;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(activeProcess.getInputStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String text = line;
                        final long now = System.currentTimeMillis();
                        final boolean updateBar = (now - lastProgressUpdate) >= 150;

                        Platform.runLater(() -> {
                            if (logArea != null) {
                                logArea.appendText(text + "\n");
                                if (logArea.getLength() > 60_000) {
                                    logArea.deleteText(0, 12_000);
                                }
                            }
                            if (statusLabel != null && !transferPaused) {
                                statusLabel.setText("Transferring...");
                            }

                            if (updateBar) {
                                Matcher m = percentPattern.matcher(text);
                                if (m.find() && progressBar != null) {
                                    try {
                                        int pct = Integer.parseInt(m.group(1));
                                        if (pct >= 0 && pct <= 100) {
                                            progressBar.setProgress(pct / 100.0);
                                            if (percentLabel != null) {
                                                percentLabel.setText(pct + "%");
                                            }
                                        }
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                            }
                        });

                        if (updateBar) {
                            lastProgressUpdate = now;
                        }
                    }
                }

                int code = activeProcess.waitFor();
                activeProcess = null;

                Platform.runLater(() -> {
                    setControlState(false, false, false);
                    if (progressBar != null) progressBar.setProgress(1.0);
                    if (percentLabel != null) percentLabel.setText("100%");
                    if (statusLabel != null) {
                        statusLabel.setText(code == 0
                                ? "Transfer completed successfully."
                                : "Transfer finished with errors.");
                    }
                });

            } catch (Exception ex) {
                activeProcess = null;
                Platform.runLater(() -> {
                    setControlState(false, false, false);
                    if (statusLabel != null) statusLabel.setText("Error: " + ex.getMessage());
                    if (logArea != null) logArea.appendText("Error: " + ex.getMessage() + "\n");
                });
            }
        }).start();
    }

    @FXML
    private void pauseTransfer() {
        Process p = activeProcess;
        if (p == null || !p.isAlive() || transferPaused) return;

        try {
            long pid = p.pid();
            new ProcessBuilder("kill", "-STOP", Long.toString(pid)).start().waitFor();
            transferPaused = true;
            setControlState(false, true, true);
            if (statusLabel != null) statusLabel.setText("Paused");
            if (logArea != null) logArea.appendText("--- Transfer paused ---\n");
        } catch (Exception ex) {
            if (logArea != null) logArea.appendText("Could not pause: " + ex.getMessage() + "\n");
        }
    }

    @FXML
    private void resumeTransfer() {
        Process p = activeProcess;
        if (p == null || !p.isAlive() || !transferPaused) return;

        try {
            long pid = p.pid();
            new ProcessBuilder("kill", "-CONT", Long.toString(pid)).start().waitFor();
            transferPaused = false;
            setControlState(true, false, true);
            if (statusLabel != null) statusLabel.setText("Transferring...");
            if (logArea != null) logArea.appendText("--- Transfer resumed ---\n");
        } catch (Exception ex) {
            if (logArea != null) logArea.appendText("Could not resume: " + ex.getMessage() + "\n");
        }
    }

    @FXML
    private void stopTransfer() {
        Process p = activeProcess;
        if (p == null) return;

        try {
            p.destroy();
            if (p.isAlive()) {
                p.destroyForcibly();
            }
            activeProcess = null;
            transferPaused = false;
            setControlState(false, false, false);
            if (statusLabel != null) statusLabel.setText("Transfer stopped by user");
            if (logArea != null) logArea.appendText("--- Transfer stopped by user ---\n");
        } catch (Exception ex) {
            if (logArea != null) logArea.appendText("Stop failed: " + ex.getMessage() + "\n");
        }
    }

    private void setControlState(boolean pauseEnabled, boolean resumeEnabled, boolean stopEnabled) {
        if (pauseBtn != null)  pauseBtn.setDisable(!pauseEnabled);
        if (resumeBtn != null) resumeBtn.setDisable(!resumeEnabled);
        if (stopBtn != null)   stopBtn.setDisable(!stopEnabled);
    }
}
