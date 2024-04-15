package com.chesapeaketechnology.networkviz;

import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;
import java.util.regex.*;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.util.WWUtil;
import gov.nasa.worldwindx.examples.ApplicationTemplate;
import gov.nasa.worldwindx.examples.GeoJSONLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.Scanner;

/**
 * Creates and manages visuals within a WorldWind based application. Default
 * controls for toggling the visibility of each
 * {@link Layer} and skeleton methods are included to help get started.
 * <p>
 * Additional documentation can be found at WorldWind's open source github -
 * https://nasaworldwind.github.io/WorldWindJava/.
 */
public class NetworkVisualizer extends ApplicationTemplate {
    /**
     * Logger instance for logging messages and errors.
     */
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Reference to the application frame.
     */
    private AppFrame appFrame;

    /**
     * String representing the URL of the GeoJSON data source.
     */
    private String URL;

    /**
     * Reference to the GeoJSON layer, it holds the layer containing the GeoJSON
     * data displayed on the WorldWindow.
     */
    Layer layer = null;

    /**
     * ScheduledExecutorService for scheduling periodic tasks.
     */
    private ScheduledExecutorService ses = null;

    /**
     * Configures and opens the WorldWind application that can be used to view
     * GeoJSON data. After this method completes,
     * the {@link #appFrame} will be initialized.
     */
    public void launchApplication() {
        appFrame = start("World Wind JSON Network Viewer", AppFrame.class);
        // Reconfigure the size of the World Window to take up the space typically used
        // by the layer panel
        Dimension dimension = new Dimension(1400, 800);
        appFrame.setPreferredSize(dimension);
        appFrame.pack();
        WWUtil.alignComponent(null, appFrame, AVKey.CENTER);

        addMenusToFrame();
    }

    /**
     * Adds menu options that visualize GeoJSON data to the WorldWind application's
     * menubar.
     */
    private void addMenusToFrame() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem openFileMenuItem = makeOpenFileMenu();
        JMenuItem urlItem = EQlink();
        JMenuItem refreshItem = RRate();
        appFrame.setJMenuBar(menuBar);
        menuBar.add(fileMenu);
        fileMenu.add(openFileMenuItem);
        fileMenu.add(urlItem);
        fileMenu.add(refreshItem);
    }

    /**
     * Runnable task to refresh the GeoJSON layer from a specified URL.
     * Loads GeoJSON data using a GeoJSONLoader, replaces the existing layer (if
     * any),
     * and adds the new layer to the WorldWindow.
     */
    Runnable refreshHelper = new Runnable() {
        public void run() {
            // Load GeoJSON data from the URL
            GeoJSONLoader loader = new GeoJSONLoader();

            // Prevents Renderable Layer label from being repeatedly added.
            if (layer != null) {
                appFrame.getWwd().getModel().getLayers().remove(layer);
            }
            // Create and add new layer to the WorldWindow
            layer = loader.createLayerFromSource(URL);
            appFrame.getWwd().getModel().getLayers().add(layer);
        }
    };

    /**
     * Creates a menu to set the refresh rate for updating the GeoJSON data using
     * ScheduledExecutorService.
     * 
     * @return A JMenuItem representing the menu option for setting the refresh
     *         rate.
     */
    private JMenuItem RRate() {
        JMenuItem refreshRate = new JMenuItem(new AbstractAction("Refresh Rate") {
            public void actionPerformed(ActionEvent actionEvent) {
                boolean valid = false;
                try {
                    // Prompt the user to enter a refresh rate in seconds
                    while (!valid) {
                        int RR = Integer.parseInt(
                                JOptionPane.showInputDialog(null, "Enter Refresh Rate (seconds) \n Enter 0 to stop"));
                        valid = true;

                        // Shutdown existing scheduled executor service if it exists
                        if (ses != null) {
                            ses.shutdown();
                        }

                        // If refresh rate is 0, stop scheduling and set executor service to null
                        if (RR == 0) {
                            ses = null;
                        } else {
                            // Create a new scheduled executor service
                            ses = Executors.newScheduledThreadPool(1);

                            // Schedule the refresh task at a fixed rate
                            ScheduledFuture<?> scheduledFuture = ses.scheduleAtFixedRate(refreshHelper, 0, RR,
                                    TimeUnit.SECONDS);
                        }
                    }
                } catch (NumberFormatException e) {
                    // Display error message if invalid input for refresh rate
                    JOptionPane.showMessageDialog(null, "Please enter a valid integer value for the refresh rate.");
                }
            }
        });

        // Keyboard shortcuts
        refreshRate.setMnemonic(KeyEvent.VK_R);
        refreshRate.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_MASK));

        return refreshRate;
    }

    /**
     * Creates a menu item to allow users to enter a URL for GeoJSON data.
     * 
     * @return A JMenuItem representing the menu option for entering a URL.
     */
    private JMenuItem EQlink() {
        JMenuItem urlItem = new JMenuItem(new AbstractAction("Open URL") {
            public void actionPerformed(ActionEvent actionEvent) {
                boolean valid = false;
                try {
                    // Prompt the user to enter a URL
                    while (!valid) {
                        String url = JOptionPane.showInputDialog(null, "Please Enter URL: ",
                                "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson");
                        System.out.println("URL1: " + url);

                        // If no URL is provided, use default URL and set as valid
                        if (url.length() == 0) {
                            URL = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson";
                            valid = true;
                        } else {
                            // Validate the URL using regex pattern
                            String urlPattern = "^(https?|ftp)://[a-zA-Z0-9.-]+(\\.[a-zA-Z]{2,4})?(/\\S*)?$";
                            Pattern pattern = Pattern.compile(urlPattern);
                            Matcher matcher = pattern.matcher(url);
                            // If URL matches the pattern, set it as valid, update URL, and trigger refresh
                            if (matcher.matches()) {
                                valid = true;
                                URL = url;
                                refreshHelper.run();
                            } else {
                                // Display error message for invalid URL
                                JOptionPane.showMessageDialog(null, "Invalid URL. Please provide a valid URL", "Error",
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // Display error message for invalid URL
                    JOptionPane.showMessageDialog(null, "Invalid URL. Please provide a valid URL", "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        // Keyboard shortcuts
        urlItem.setMnemonic(KeyEvent.VK_U);
        urlItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.CTRL_MASK));

        return urlItem;
    }

    /**
     * Creates a menu option to allow users to view GeoJSON data on the WorldWind
     * canvas.
     * 
     * @return A menu option that can be added to an application's menu bar.
     */
    private JMenuItem makeOpenFileMenu() {
        // Create a file chooser instance and add a file filter for JSON files
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("JSON File", "json", "json"));

        // Create a menu item to handle the file opening process
        JMenuItem openFileMenuItem = new JMenuItem(new AbstractAction("Open File...") {
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    // Show the file chooser dialog and get the user's selection
                    int status = fileChooser.showOpenDialog(appFrame);
                    if (status == JFileChooser.APPROVE_OPTION) {
                        // Get the selected file
                        File file = fileChooser.getSelectedFile();

                        // Read the file // not used its just console log stuff
                        Scanner scanner = new Scanner(file);
                        String jsoString = new String();
                        while (scanner.hasNextLine()) {
                            String line = scanner.nextLine();
                            jsoString += line;
                        }
                        scanner.close();

                        // Load GeoJSON layer from the selected file
                        GeoJSONLoader ldr = new GeoJSONLoader();
                        Layer layer = ldr.createLayerFromSource(file);

                        // Add the loaded layer to the WorldWindow
                        if (ldr != null) {
                            appFrame.getWwd().getModel().getLayers().add(layer);
                        }
                    }
                } catch (IOException e) {
                    // Handle invalid file exception
                    System.out.println("Invalid File");
                }
            }
        });
        // Keyboard shortcuts
        openFileMenuItem.setMnemonic(KeyEvent.VK_F);
        openFileMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_MASK));
        return openFileMenuItem;
    }

    public static void main(String[] args) {
        NetworkVisualizer networkVisualizer = new NetworkVisualizer();
        networkVisualizer.launchApplication();
    }
}