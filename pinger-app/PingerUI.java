import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class PingerUI {

    private static JTextField ipAddressField;
    private static JTextArea resultTextArea;
    private static JLabel statusLabel;
    private static JButton pingButton;
    private static JButton tracerouteButton;
    private static JButton nslookupButton;
    private static JButton clearButton;
    private static boolean isTracerouteInProgress = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(PingerUI::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Pinger App");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout());

        JLabel label = new JLabel("Enter IP Address:");
        ipAddressField = new JTextField();
        ipAddressField.setColumns(15);
        pingButton = new JButton("Ping");
        tracerouteButton = new JButton("Traceroute");
        nslookupButton = new JButton("Nslookup");
        clearButton = new JButton("Clear Result");
        resultTextArea = new JTextArea();
        resultTextArea.setEditable(false);
        resultTextArea.setRows(20);
        resultTextArea.setColumns(30);
        JScrollPane resultScrollPane = new JScrollPane(resultTextArea);

        statusLabel = new JLabel(" ");
        statusLabel.setHorizontalAlignment(JLabel.CENTER);

        pingButton.addActionListener(e -> {
            String ipAddress = ipAddressField.getText();
            if (!ipAddress.isEmpty()) {
                executePing(ipAddress);
            } else {
                appendResult("Please enter an IP address");
            }
        });

        tracerouteButton.addActionListener(e -> {
            String ipAddress = ipAddressField.getText();
            if (!ipAddress.isEmpty() && !isTracerouteInProgress) {
                isTracerouteInProgress = true;
                executeTraceroute(ipAddress);
            } else if (isTracerouteInProgress) {
                appendResult("Traceroute is already in progress. Please wait for it to finish.");
            } else {
                appendResult("Please enter an IP address");
            }
        });

        nslookupButton.addActionListener(e -> {
            String ipAddress = ipAddressField.getText();
            if (!ipAddress.isEmpty()) {
                executeNslookup(ipAddress);
            } else {
                appendResult("Please enter an IP address");
            }
        });

        clearButton.addActionListener(e -> resultTextArea.setText(""));

        JPanel inputPanel = new JPanel(new FlowLayout());
        inputPanel.add(label);
        inputPanel.add(ipAddressField);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(pingButton);
        buttonPanel.add(tracerouteButton);
        buttonPanel.add(nslookupButton);
        buttonPanel.add(clearButton);

        panel.add(inputPanel, BorderLayout.NORTH);
        panel.add(buttonPanel, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.SOUTH);
        panel.add(resultScrollPane, BorderLayout.SOUTH);

        frame.getContentPane().add(BorderLayout.CENTER, panel);
        frame.setSize(700, 500);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void executePing(String ipAddress) {
        PingTracerouteNslookupWorker worker = new PingTracerouteNslookupWorker(ipAddress, true, null);
        worker.execute();
    }

    private static void executeTraceroute(String ipAddress) {
        PingTracerouteNslookupWorker worker = new PingTracerouteNslookupWorker(ipAddress, false, null);
        worker.execute();
    }

    private static void executeNslookup(String ipAddress) {
        PingTracerouteNslookupWorker worker = new PingTracerouteNslookupWorker(ipAddress, false, "nslookup");
        worker.execute();
    }

    private static class PingTracerouteNslookupWorker extends SwingWorker<Void, String> {

        private final String ipAddress;
        private final boolean isPing;
        private final String command;

        public PingTracerouteNslookupWorker(String ipAddress, boolean isPing, String command) {
            this.ipAddress = ipAddress;
            this.isPing = isPing;
            this.command = command;
        }

        @Override
        protected Void doInBackground() {
            setButtonsEnabled(false);
            try {
                InetAddress inetAddress = InetAddress.getByName(ipAddress);
                if (!isPing) {
                    if ("nslookup".equals(command)) {
                        executeNslookupCommand(ipAddress);
                    } else {
                        executeTracerouteCommand(ipAddress);
                    }
                } else {
                    for (int i = 0; i < 4; i++) {
                        long startTime = System.currentTimeMillis();
                        boolean isReachable = inetAddress.isReachable(5000);
                        long endTime = System.currentTimeMillis();
                        long timeElapsed = endTime - startTime;

                        if (isReachable) {
                            publish("Reply from " + inetAddress.getHostAddress() +
                                    ": bytes=32 time=" + timeElapsed + "ms TTL=128");
                        } else {
                            publish("Request timed out.");
                        }
                    }
                }
            } catch (UnknownHostException e) {
                publish("Unknown host: " + ipAddress);
            } catch (IOException e) {
                e.printStackTrace();
                publish("An error occurred while " + (isPing ? "pinging" : "performing traceroute or nslookup"));
            }

            return null;
        }

        @Override
        protected void process(java.util.List<String> chunks) {
            for (String chunk : chunks) {
                appendResult(chunk);
            }
        }

        @Override
        protected void done() {
            setButtonsEnabled(true);
            isTracerouteInProgress = false;
            appendResult("");
        }

        private void setButtonsEnabled(boolean enabled) {
            pingButton.setEnabled(enabled);
            tracerouteButton.setEnabled(enabled);
            nslookupButton.setEnabled(enabled);
            clearButton.setEnabled(enabled);
        }

        private void executeNslookupCommand(String ipAddress) throws IOException {
            ProcessBuilder processBuilder = new ProcessBuilder("nslookup", ipAddress);
            Process nslookupProcess = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(nslookupProcess.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                publish(line);
            }

            try {
                int exitCode = nslookupProcess.waitFor();
                if (exitCode != 0) {
                    publish("nslookup failed. Exit code: " + exitCode);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                publish("nslookup interrupted.");
            }
        }

        private void executeTracerouteCommand(String ipAddress) throws IOException {
            ProcessBuilder processBuilder = new ProcessBuilder("tracert", ipAddress);
            Process tracertProcess = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(tracertProcess.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                publish(line);
            }

            if (tracertProcess.exitValue() != 0) {
                publish("Traceroute failed. Please check the IP address and try again.");
            }
        }
    }

    private static void appendResult(String text) {
        resultTextArea.append(text + "\n");
    }
}
