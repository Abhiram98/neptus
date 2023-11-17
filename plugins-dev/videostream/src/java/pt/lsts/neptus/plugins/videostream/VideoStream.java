/*
 * Copyright (c) 2004-2023 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * Modified European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the Modified EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://github.com/LSTS/neptus/blob/develop/LICENSE.md
 * and http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: Pedro Gonçalves
 * Apr 4, 2015
 */
package pt.lsts.neptus.plugins.videostream;

import com.google.common.eventbus.Subscribe;
import foxtrot.AsyncTask;
import foxtrot.AsyncWorker;
import net.miginfocom.swing.MigLayout;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import pt.lsts.imc.CcuEvent;
import pt.lsts.imc.EstimatedState;
import pt.lsts.imc.MapFeature;
import pt.lsts.imc.MapPoint;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.console.events.ConsoleEventMainSystemChange;
import pt.lsts.neptus.console.notifications.Notification;
import pt.lsts.neptus.i18n.I18n;
import pt.lsts.neptus.mp.preview.payloads.CameraFOV;
import pt.lsts.neptus.params.ConfigurationManager;
import pt.lsts.neptus.params.SystemProperty;
import pt.lsts.neptus.params.SystemProperty.Scope;
import pt.lsts.neptus.params.SystemProperty.Visibility;
import pt.lsts.neptus.platform.OsInfo;
import pt.lsts.neptus.platform.OsInfo.Family;
import pt.lsts.neptus.plugins.NeptusProperty;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.Popup;
import pt.lsts.neptus.plugins.Popup.POSITION;
import pt.lsts.neptus.plugins.update.Periodic;
import pt.lsts.neptus.renderer2d.LayerPriority;
import pt.lsts.neptus.types.coord.LocationType;
import pt.lsts.neptus.types.map.AbstractElement;
import pt.lsts.neptus.types.map.MapGroup;
import pt.lsts.neptus.types.map.MapType;
import pt.lsts.neptus.types.map.MarkElement;
import pt.lsts.neptus.types.mission.MapMission;
import pt.lsts.neptus.types.mission.MissionType;
import pt.lsts.neptus.util.FileUtil;
import pt.lsts.neptus.util.ImageUtils;
import pt.lsts.neptus.util.SearchOpenCv;
import pt.lsts.neptus.util.UtilCv;
import pt.lsts.neptus.util.conf.ConfigFetch;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Neptus Plugin for Video Stream and tag frame/object
 *
 * @author pedrog
 * @author Pedro Costa
 */
@Popup(pos = POSITION.CENTER, width = 640, height = 480, accelerator = 'R')
@LayerPriority(priority = 0)
@PluginDescription(name = "Video Stream", version = "1.5.1", author = "Pedro Gonçalves",
        description = "Plugin to view IP Camera streams", icon = "images/menus/camera.png",
        category = PluginDescription.CATEGORY.INTERFACE)
public class VideoStream extends ConsolePanel {
    private static final String BASE_FOLDER_FOR_IMAGES = ConfigFetch.getLogsFolder() + "/images";
    static final String BASE_FOLDER_FOR_URL_INI = "ipUrl.ini";
    // Default width and height of Console
    private static final int DEFAULT_WIDTH_CONSOLE = 640;
    private static final int DEFAULT_HEIGHT_CONSOLE = 480;

    // Timeout for watchDogThread in milliseconds
    private static final int WATCH_DOG_TIMEOUT_MILLIS = 4000;
    private static final int WATCH_DOG_LOOP_THREAD_TIMEOUT_MILLIS = 6000;

    private static final int MAX_NULL_FRAMES_FOR_RECONNECT = 10;

    private final Color LABEL_WHITE_COLOR = new Color(255, 255, 255, 200);

    @NeptusProperty(name = "Camera URL", editable = false)
    private String camUrl = ""; //rtsp://10.0.20.207:554/live/ch01_0

    @NeptusProperty(name = "Broadcast positions to other CCUs", editable = true)
    private boolean broadcastPositions = false;

    private AtomicInteger emptyFramesCounter = new AtomicInteger(0);
    private AtomicInteger threadsIdCounter = new AtomicInteger(0);

    // Buffer for info of data image
    private BufferedReader in = null;
    // Strut Video Capture Opencv
    private VideoCapture capture;
    private VideoCapture captureSave;
    // Width size of image
    private int widthImgRec;
    // Height size of image
    private int heightImgRec;
    // Width size of Console
    private int widthConsole = DEFAULT_WIDTH_CONSOLE;
    // Height size of Console
    private int heightConsole = DEFAULT_HEIGHT_CONSOLE;
    // Black Image
    private Scalar black = new Scalar(0);
    // flag for state of neptus logo
    private boolean noVideoLogoState = false;
    // Scale factor of x pixel
    private float xScale;
    // Scale factor of y pixel
    private float yScale;
    // Buffer image for showImage
    private BufferedImage offlineImage;
    private BufferedImage onScreenImage;
    private BufferedImage onScreenImageLastGood;
    // Flag - Lost connection to the vehicle
    private boolean state = false;
    // Flag - Show/hide Menu JFrame
    private boolean show_menu = false;
    // Flag state of IP CAM
    private boolean ipCam = false;
    // Url of IPCam
    private ArrayList<Camera> cameraList;
    private boolean closingPanel = false;
    private boolean refreshTemp;

    // JPanel for info and config values
    private JPanel config;
    // JText info of data receive
    private JLabel txtText;
    // JText of data receive over IMC message
    private JLabel txtData;
    // JText of data warning message
    private JLabel warningText;
    // JFrame for menu options
    private JDialog menu;
    // JPopup Menu
    private JPopupMenu popup;
    // JLabel for image
    private JLabel streamNameJLabel;
    private JLabel streamWarnJLabel;

    // Flag to enable/disable zoom
    private boolean zoomMask = false;
    // String for the info treatment
    private String info;
    // String for the info of Image Size Stream
    private String infoSizeStream;
    // Data system
    private Date date = new Date();
    // Location of log folder
    private String logDir;
    // Image resize
    private Mat matResize;
    // Image receive
    private Mat mat;
    // Image receive to save
    private Mat matSaveImg;
    // Size of output frame
    private Size size = null;

    // counter for frame tag ID
    private short frameTagID = 1;

    // JPanel for color state of ping to host IPCam
    private JPanel colorStateIPCam;
    // JDialog for IPCam Select
    private JDialog ipCamPing;
    // JButton to confirm IPCam
    private JButton selectIPCam;
    // JComboBox for list of IPCam in ipUrl.ini
    private JComboBox<Camera> ipCamList;
    // row select from string matrix of IPCam List
    private int selectedItemIndex;
    // JLabel for text IPCam Ping
    private JLabel onOffIndicator;
    // JTextField for IPCam name
    private JTextField fieldName = new JTextField(I18n.text("Name"));
    // JTextField for IPCam ip
    private JTextField fieldIP = new JTextField(I18n.text("IP"));
    // JTextField for IPCam url
    private JTextField fieldUrl = new JTextField(I18n.text("URL"));
    // JPanel for zoom point
    private JPanel zoomImg = new JPanel();
    // Buffer image for zoom Img Cut
    private BufferedImage zoomImgCut;
    // JLabel to show zoom image
    private JLabel zoomLabel = new JLabel();
    // Graphics2D for zoom image scaling
    private Graphics2D graphics2D;
    // BufferedImage for zoom image scaling
    private BufferedImage scaledCutImage;
    // Buffer image for zoom image temp
    private BufferedImage zoomTemp;
    // PopPup zoom Image
    private JPopupMenu popupZoom;
    // cord x for zoom
    private int zoomX = 100;
    // cord y for zoom
    private int zoomY = 100;

    // Flag for Histogram image
    private boolean histogramFlag = false;
    // Flag to save snapshot
    private boolean saveSnapshot = false;

    // *** TEST FOR SAVE VIDEO **/
    private File outputFile;
    private boolean flagBuffImg = false;
    private int cnt = 0;
    private int fps = 8;
    // *************************/

    // worker thread designed to acquire the data packet from DUNE
    private Thread updater = null;
    // worker thread designed to save image do HD
    private Thread saveImg = null;
    // worker thread create ipUrl.ini in conf folder
    private Thread createIPUrl = null;

    // WatchDog variables/objects
    private Thread watchDog;
    private long endTimeMillis;
    private boolean virtualEndThread;
    private boolean isAliveIPCam;
    private boolean isCleanTurnOffCam;
    private CameraFOV camFov = null;
    private Point2D mouseLoc = null;
    private StoredSnapshot snap = null;
    private boolean paused = false;
    private AtomicLong captureLoopAtomicLongMillis = new AtomicLong(-1);

    public VideoStream(ConsoleLayout console) {
        super(console);

        // Initialize size variables
        updateSizeVariables(this);

        if (findOpenCV()) {
            NeptusLog.pub().info(I18n.text("OpenCv-4.x.x found."));
            // clears all the unused initializations of the standard ConsolePanel
            removeAll();
            // Resize Console
            this.addComponentListener(new ComponentAdapter() {
                public void componentResized(ComponentEvent evt) {
                    Component c = evt.getComponent();
                    updateSizeVariables(c);
                    matResize = new Mat((int) size.height, (int) size.width, CvType.CV_8UC3);
                    if (!ipCam) {
                        initImage();
                    }
                }
            });

            this.setToolTipText(I18n.text("not connected"));

            // Mouse click
            mouseListenerInit();

            // Detect key-pressed
            this.addKeyListener(new KeyListener() {
                @Override
                public void keyReleased(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_Z && zoomMask) {
                        zoomMask = false;
                        popupZoom.setVisible(false);
                    }
                    if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
                        paused = false;
                    }
                }

                @Override
                public void keyPressed(KeyEvent e) {
                    if ((e.getKeyCode() == KeyEvent.VK_Z) && ((e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0)
                            && !zoomMask) {
                        if (ipCam) {
                            zoomMask = true;
                            popupZoom.add(zoomImg);
                        }
                    }
                    else if ((e.getKeyCode() == KeyEvent.VK_I)
                            && ((e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0)) {
                        openIPCamManagementPanel();
                    }
                    else if ((e.getKeyCode() == KeyEvent.VK_X)
                            && ((e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0)) {
                        NeptusLog.pub().info("Closing all Video Streams...");
                        state = false;
                        ipCam = false;
                    }
                    else if ((e.getKeyCode() == KeyEvent.VK_C)
                            && ((e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0)) {
                        menu.setVisible(true);
                    }
                    else if (e.getKeyChar() == 'z' && zoomMask) {
                        int xLocMouse = MouseInfo.getPointerInfo().getLocation().x - getLocationOnScreen().x - 11;
                        int yLocMouse = MouseInfo.getPointerInfo().getLocation().y - getLocationOnScreen().y - 11;
                        if (xLocMouse < 0) {
                            xLocMouse = 0;
                        }
                        if (yLocMouse < 0) {
                            yLocMouse = 0;
                        }

                        if (xLocMouse + 52 < VideoStream.this.getSize().getWidth() && xLocMouse - 52 > 0
                                && yLocMouse + 60 < VideoStream.this.getSize().getHeight() && yLocMouse - 60 > 0) {
                            zoomX = xLocMouse;
                            zoomY = yLocMouse;
                            popupZoom.setLocation(MouseInfo.getPointerInfo().getLocation().x - 150,
                                    MouseInfo.getPointerInfo().getLocation().y - 150);
                        }
                        else {
                            popupZoom.setVisible(false);
                            zoomMask = false;
                        }
                    }
                    else if ((e.getKeyCode() == KeyEvent.VK_H)
                            && ((e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0)) {
                        histogramFlag = !histogramFlag;
                    }
                    else if ((e.getKeyCode() == KeyEvent.VK_S)
                            && ((e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0)) {
                        saveSnapshot = true;
                    }
                    else if ((e.getKeyCode() == KeyEvent.VK_CONTROL)) {
                        paused = true;
                    }
                    else if ((e.getKeyCode() == KeyEvent.VK_F)
                            && e.getModifiersEx() == KeyEvent.ALT_DOWN_MASK) {
                        maximizeVideoStreamPanel();
                    }
                }

                @Override
                public void keyTyped(KeyEvent e) {
                }
            });
            this.setFocusable(true);
        }
        else {
            NeptusLog.pub().error("Opencv not found.");
            closingPanel = true;
            setBackground(Color.BLACK);
            // JLabel for image
            this.setLayout(new MigLayout("al center center"));
            // JLabel info
            String opencvInstallLink = "";
            if (OsInfo.getFamily() == Family.UNIX) {
                opencvInstallLink = "<br>" + I18n.text(
                        "Install OpenCv 4.4 and dependencies at <br>https://www.lsts.pt/bin/opencv/v4.4.0-x64_x86/deb/");
            }
            else if (OsInfo.getFamily() == Family.WINDOWS) {
                opencvInstallLink = "<br>" + I18n.text(
                        "Install OpenCv 4.4 and dependencies at <br>https://www.lsts.pt/bin/opencv/v4.4.0-x64_x86/win-x64_86/");
            }
            warningText = new JLabel(
                    "<html>" + I18n.text("Please install OpenCV 4.4.0 and its dependencies." + opencvInstallLink));
            warningText.setForeground(Color.BLACK);
            warningText.setFont(new Font("Courier New", Font.ITALIC, 18));
            this.add(warningText);
        }

        streamNameJLabel = new JLabel();
        streamNameJLabel.setForeground(LABEL_WHITE_COLOR);
        streamNameJLabel.setBackground(new Color(0, 0, 0, 80));
        streamNameJLabel.setOpaque(true);
        streamNameJLabel.setHorizontalAlignment(SwingConstants.CENTER);
        streamNameJLabel.setVerticalAlignment(SwingConstants.TOP);
        streamNameJLabel.setVerticalTextPosition(SwingConstants.TOP);

        streamWarnJLabel = new JLabel();
        streamWarnJLabel.setForeground(LABEL_WHITE_COLOR);
        streamWarnJLabel.setOpaque(false);
        streamWarnJLabel.setHorizontalAlignment(SwingConstants.CENTER);
        streamWarnJLabel.setVerticalAlignment(SwingConstants.BOTTOM);
        streamWarnJLabel.setVerticalTextPosition(SwingConstants.BOTTOM);
    }

    @Periodic(millisBetweenUpdates = 1_000)
    public void updateToolTip() {
        String tooltipText = I18n.text("not connected");
        if (ipCam && !state) {
            tooltipText = I18n.text("connecting to") + " " + fieldName.getText();
        }
        else if (ipCam) {
            tooltipText = I18n.text("streaming from") + " " + fieldName.getText();
        }
        this.setToolTipText(I18n.text(tooltipText));

        if (!ipCam) {
            onScreenImageLastGood = offlineImage;
        }
        repaint(500);
    }

    private void updateSizeVariables(Component comp) {
        widthConsole = comp.getSize().width;
        heightConsole = comp.getSize().height;
        xScale = (float) widthConsole / widthImgRec;
        yScale = (float) heightConsole / heightImgRec;
        size = new Size(widthConsole, heightConsole);
    }

    // Mouse click Listener
    private void mouseListenerInit() {

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (camFov != null) {
                    double width = ((Component) e.getSource()).getWidth();
                    double height = ((Component) e.getSource()).getHeight();
                    double x = e.getX();
                    double y = height - e.getY();
                    mouseLoc = new Point2D.Double((x / width - 0.5) * 2, (y / height - 0.5) * 2);
                }
            }
        });

        addMouseListener(new MouseAdapter() {

            @Override
            public void mouseExited(MouseEvent e) {
                mouseLoc = null;
                post(new EventMouseLookAt(null));
            }

            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.isControlDown()) {
                    if (camFov != null) {
                        double width = ((Component) e.getSource()).getWidth();
                        double height = ((Component) e.getSource()).getHeight();
                        double x = e.getX();
                        double y = height - e.getY();
                        mouseLoc = new Point2D.Double((x / width - 0.5) * 2, (y / height - 0.5) * 2);
                        LocationType loc = camFov.getLookAt(mouseLoc.getX(), mouseLoc.getY());
                        loc.convertToAbsoluteLatLonDepth();
                        String id = placeLocationOnMap(loc);
                        snap = new StoredSnapshot(id, loc, e.getPoint(), onScreenImage, new Date());
                        snap.setCamFov(camFov);
                        try {
                            snap.store();
                        }
                        catch (Exception ex) {
                            NeptusLog.pub().error(ex);
                        }
                    }
                }

                if (e.getButton() == MouseEvent.BUTTON3) {
                    popup = new JPopupMenu();
                    JMenuItem item;

                    popup.add(item = new JMenuItem(I18n.text("Connect to a IPCam"),
                                    ImageUtils.createImageIcon("images/menus/camera.png")))
                            .addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    openIPCamManagementPanel();
                                }
                            });
                    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.ALT_MASK));

                    popup.add(item = new JMenuItem(I18n.text("Close connection"),
                                    ImageUtils.createImageIcon("images/menus/exit.png")))
                            .addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    NeptusLog.pub().info("Closing video streams");
                                    noVideoLogoState = false;
                                    isCleanTurnOffCam = true;
                                    state = false;
                                    ipCam = false;
                                    if (capture != null && capture.isOpened()) {
                                        try {
                                            capture.release();
                                            NeptusLog.pub().info("Capture successfully released");
                                        } catch (Exception | Error exp) {
                                            NeptusLog.pub().warn("Capture error releasing :" + exp.getMessage());
                                        }
                                    }
                                    repaint(500);
                                }
                            });
                    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.ALT_MASK));

                    popup.addSeparator();

                    // FIXME Config for now is hidden
//                    popup.add(item = new JMenuItem(I18n.text("Config"),
//                                    ImageUtils.createImageIcon(String.format("images/menus/configure.png"))))
//                            .addActionListener(new ActionListener() {
//                                @Override
//                                public void actionPerformed(ActionEvent e) {
//                                    menu.setVisible(true);
//                                }
//                            });
//                    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_MASK));

                    popup.add(item = new JMenuItem(I18n.text("Toggle Histogram filter"),
                                    ImageUtils.createImageIcon("images/menus/histogram.png")))
                            .addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    histogramFlag = !histogramFlag;
                                }
                            });
                    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.ALT_MASK));

                    JCheckBoxMenuItem itemChecked;
                    popup.add(itemChecked = new JCheckBoxMenuItem("Save stream as images to disk", flagBuffImg));
                    itemChecked.addItemListener(e1 -> flagBuffImg = e1.getStateChange() == ItemEvent.SELECTED);

                    popup.add(item = new JMenuItem(I18n.text("Take snapshot"),
                                    ImageUtils.createImageIcon("images/menus/snapshot.png")))
                            .addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    saveSnapshot = true;
                                }
                            });
                    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.ALT_MASK));

                    popup.add(item = new JMenuItem(I18n.text("Maximize window"),
                                    ImageUtils.createImageIcon("images/menus/maximize.png")))
                            .addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    maximizeVideoStreamPanel();
                                }
                            });
                    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.ALT_MASK));

                    popup.addSeparator();

                    JLabel infoZoom = new JLabel(I18n.text("For zoom use Alt-Z"));
                    infoZoom.setEnabled(false);
                    popup.add(infoZoom, JMenuItem.CENTER_ALIGNMENT);

                    JLabel markSnap = new JLabel(I18n.text("Ctr+Click to mark frame in the map"));
                    markSnap.setEnabled(false);
                    popup.add(markSnap, JMenuItem.CENTER_ALIGNMENT);

                    popup.show((Component) e.getSource(), e.getX(), e.getY());
                }
            }
        });
    }

    private void maximizeVideoStreamPanel() {
        JDialog dialog = (JDialog) SwingUtilities.getWindowAncestor(VideoStream.this);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize().getSize();
        if (dialog.getSize().equals(screenSize)) {
            // Already maximized
            screenSize = new Dimension(DEFAULT_WIDTH_CONSOLE, DEFAULT_HEIGHT_CONSOLE);
        }
        dialog.setSize(screenSize);
        // We call the resize with its own size to call componentResized
        // method of the componentAdapter set in the constructor
        VideoStream.this.resize(VideoStream.this.getSize());
    }

    // Read ipUrl.ini to find IPCam ON
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void openIPCamManagementPanel() {
        // JPanel for IPCam Select (MigLayout)
        JPanel ipCamManagementPanel = new JPanel(new MigLayout());

        repaintParametersTextFields();
        cameraList = readIPUrl();

        URI uri = UtilVideoStream.getCamUrlAsURI(camUrl);

        ipCamPing = new JDialog(SwingUtilities.getWindowAncestor(VideoStream.this), I18n.text("Select IPCam"));
        ipCamPing.setResizable(true);
        ipCamPing.setModalityType(ModalityType.DOCUMENT_MODAL);
        ipCamPing.setSize(440, 200);
        ipCamPing.setLocationRelativeTo(VideoStream.this);

        ImageIcon imgIPCam = ImageUtils.createImageIcon("images/menus/camera.png");
        ipCamPing.setIconImage(imgIPCam.getImage());
        ipCamPing.setResizable(false);
        ipCamPing.setBackground(Color.GRAY);

        int sel = 0;
        if (uri != null || uri != null && uri.getScheme() != null) {
            String host = uri.getHost();
            String name = "Stream " + uri.getScheme() + "@" + uri.getPort();
            Camera cam = new Camera(name, host, camUrl);
            NeptusLog.pub().info("Cam > " + cam +  " | host " + host+ " | URI " + camUrl + " | " + cam.getUrl());
            Camera matchCam = cameraList.stream().filter(c -> c.getUrl().equalsIgnoreCase(cam.getUrl()))
                    .findAny().orElse(null);

            if (matchCam == null) {
                cameraList.add(1, cam);
                sel = 1;
            }
            else {
                int index = -1;
                for (int i = 0; i < cameraList.size(); i++) {
                    Camera c = cameraList.get(i);
                    if (c == matchCam) {
                        index = i;
                        break;
                    }
                }
                sel = index;
                if (index < 0) {
                    cameraList.add(1, cam);
                    sel = 1;
                }
            }
        }

        ipCamList = new JComboBox(cameraList.toArray());
        ipCamList.setSelectedIndex(0);
        ipCamList.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ipCamList.setEnabled(false);
                selectIPCam.setEnabled(false);
                selectedItemIndex = ipCamList.getSelectedIndex();
                if (selectedItemIndex > 0) {
                    Camera selectedCamera = cameraList.get(selectedItemIndex);
                    colorStateIPCam.setBackground(Color.LIGHT_GRAY);
                    onOffIndicator.setText("---");

                    repaintParametersTextFields(selectedCamera.getName(), selectedCamera.getIp(), selectedCamera.getUrl());

                    AsyncTask task = new AsyncTask() {
                        boolean reachable;

                        @Override
                        public Object run() throws Exception {
                            reachable = UtilVideoStream.hostIsReachable(selectedCamera.getIp());
                            return null;
                        }

                        @Override
                        public void finish() {
                            if (reachable) {
                                selectIPCam.setEnabled(true);
                                camUrl = selectedCamera.getUrl();
                                colorStateIPCam.setBackground(Color.GREEN);
                                onOffIndicator.setText("ON");
                                ipCamList.setEnabled(true);
                            }
                            else {
                                selectIPCam.setEnabled(false);
                                colorStateIPCam.setBackground(Color.RED);
                                onOffIndicator.setText("OFF");
                                ipCamList.setEnabled(true);
                            }
                            selectIPCam.validate();
                            selectIPCam.repaint();
                        }
                    };
                    AsyncWorker.getWorkerThread().postTask(task);
                }
                else {
                    colorStateIPCam.setBackground(Color.RED);
                    onOffIndicator.setText("OFF");
                    ipCamList.setEnabled(true);
                    repaintParametersTextFields();
                }
            }
        });
        ipCamManagementPanel.add(ipCamList, "split 3, width 50:250:250, center");

        colorStateIPCam = new JPanel();
        onOffIndicator = new JLabel(I18n.text("OFF"));
        onOffIndicator.setFont(new Font("Verdana", Font.BOLD, 14));
        colorStateIPCam.setBackground(Color.RED);
        colorStateIPCam.add(onOffIndicator);
        ipCamManagementPanel.add(colorStateIPCam, "h 30!, w 30!");

        selectIPCam = new JButton(I18n.text("Connect"), imgIPCam);
        selectIPCam.setEnabled(false);
        selectIPCam.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                NeptusLog.pub().info("IPCam Select: " + cameraList.get(selectedItemIndex));
                ipCamPing.setVisible(false);
                ipCam = true;
                state = false;
            }
        });
        fieldIP.setEditable(false);
        ipCamManagementPanel.add(selectIPCam, "h 30!, wrap");

        JButton addNewIPCam = new JButton(I18n.text("Add New IPCam"));
        addNewIPCam.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Execute when button is pressed
                if (fieldName.getText().trim().isEmpty()) return;
                if (fieldIP.getText().trim().isEmpty()) return;
                if (fieldUrl.getText().trim().isEmpty()) return;
                if (UtilVideoStream.getHostFromURI(fieldUrl.getText().trim()) == null) return;

                Camera camToAdd = UtilVideoStream.parseLineCamera(String.format("%s#%s#%s\n", fieldName.getText().trim(),
                        fieldIP.getText().trim(), fieldUrl.getText().trim()));
                if (camToAdd != null) {
                    String ipUrlFilename = ConfigFetch.getConfFolder() + "/" + BASE_FOLDER_FOR_URL_INI;
                    UtilVideoStream.addCamToFile(camToAdd, ipUrlFilename);
                    reloadIPCamList();
                }
            }
        });

        JButton removeIpCam = new JButton(I18n.text("Remove IPCam"));
        removeIpCam.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Camera camToRemove = (Camera) ipCamList.getSelectedItem();
                String ipUrlFilename = ConfigFetch.getConfFolder() + "/" + BASE_FOLDER_FOR_URL_INI;
                // Execute when button is pressed
                UtilVideoStream.removeCamFromFile(camToRemove, ipUrlFilename);
                reloadIPCamList();
            }
        });

        fieldUrl.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                updateIPFieldFromUrlField();
            }
        });
        fieldUrl.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                updateIPFieldFromUrlField();
            }
        });

        ipCamManagementPanel.add(fieldName, "w 410!, wrap");
        ipCamManagementPanel.add(fieldIP, "w 410!, wrap");
        ipCamManagementPanel.add(fieldUrl, "w 410!, wrap");
        ipCamManagementPanel.add(addNewIPCam, "split 2, width 120!, center, gap related");
        ipCamManagementPanel.add(removeIpCam, "w 120!");

        ipCamPing.add(ipCamManagementPanel);
        ipCamPing.pack();

        if (sel > 0) {
            ipCamList.setSelectedIndex(sel);
        }

        ipCamPing.setVisible(true);
    }

    private void updateIPFieldFromUrlField() {
        String host = UtilVideoStream.getHostFromURI(fieldUrl.getText());
        if (host != null) {
            fieldIP.setText(host);
            fieldIP.validate();
            fieldIP.repaint(200);
        }
    }

    private void repaintParametersTextFields(String name, String ip, String url) {
        fieldName.setText(name);
        fieldName.validate();
        fieldName.repaint();
        fieldIP.setText(ip);
        fieldIP.validate();
        fieldIP.repaint();
        fieldUrl.setText(url);
        fieldUrl.validate();
        fieldUrl.repaint();
    }

    private void repaintParametersTextFields() {
        repaintParametersTextFields("NAME", "IP", "URL");
    }

    // Reloads the list of IP cams
    private void reloadIPCamList() {
        AsyncTask task = new AsyncTask() {
            @Override
            public Object run() throws Exception {
                cameraList = readIPUrl();
                return null;
            }

            @Override
            public void finish() {
                int itemCount = ipCamList.getItemCount();
                ipCamList.removeAllItems();
                for (Camera camera : cameraList) {
                    ipCamList.addItem(camera);
                }

                // If an item was added select that item
                if (itemCount < ipCamList.getItemCount()) {
                    ipCamList.setSelectedIndex(ipCamList.getItemCount() - 1);
                }
            }
        };
        AsyncWorker.getWorkerThread().postTask(task);
    }

    // Read file
    private ArrayList<Camera> readIPUrl() {
        String iniRsrcPath = FileUtil.getResourceAsFileKeepName(BASE_FOLDER_FOR_URL_INI);
        File confIni = new File(ConfigFetch.getConfFolder() + "/" + BASE_FOLDER_FOR_URL_INI);
        if (!confIni.exists()) {
            FileUtil.copyFileToDir(iniRsrcPath, ConfigFetch.getConfFolder());
        }
        return UtilVideoStream.readIpUrl(confIni);
    }

    private String timestampToReadableHoursString(long timestamp) {
        Date date = new Date(timestamp);
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
        return format.format(date);
    }

    /**
     * Adapted from ContactMarker.placeLocationOnMap()
     */
    private String placeLocationOnMap(LocationType loc) {
        if (getConsole().getMission() == null) {
            return null;
        }

        loc.convertToAbsoluteLatLonDepth();
        double lat = loc.getLatitudeDegs();
        double lon = loc.getLongitudeDegs();
        long timestamp = System.currentTimeMillis();
        String id = I18n.text("Snap") + "-" + frameTagID + "-" + timestampToReadableHoursString(timestamp);

        if (loc.equals(LocationType.ABSOLUTE_ZERO)) {
            // Don't create map elements on lat/lon zero
            return id;
        }

        AbstractElement elems[] = MapGroup.getMapGroupInstance(getConsole().getMission()).getMapObjectsByID(id);

        while (elems.length > 0) {
            frameTagID++;
            id = I18n.text("Snap") + "-" + frameTagID + "-" + timestampToReadableHoursString(timestamp);
            elems = MapGroup.getMapGroupInstance(getConsole().getMission()).getMapObjectsByID(id);
        }
        frameTagID++;// increment ID

        MissionType mission = getConsole().getMission();
        LinkedHashMap<String, MapMission> mapList = mission.getMapsList();
        if (mapList == null) {
            return id;
        }
        if (mapList.size() == 0) {
            return id;
        }
        // MapMission mapMission = mapList.values().iterator().next();
        MapGroup.resetMissionInstance(getConsole().getMission());
        MapType mapType = MapGroup.getMapGroupInstance(getConsole().getMission()).getMaps()[0];// mapMission.getMap();
        // NeptusLog.pub().info("<###>MARKER --------------- " + mapType.getId());
        MarkElement contact = new MarkElement(mapType.getMapGroup(), mapType);

        contact.setId(id);
        contact.setCenterLocation(new LocationType(lat, lon));
        mapType.addObject(contact);
        mission.save(false);
        MapPoint point = new MapPoint();
        point.setLat(lat);
        point.setLon(lon);
        point.setAlt(0);
        MapFeature feature = new MapFeature();
        feature.setFeatureType(MapFeature.FEATURE_TYPE.POI);
        feature.setFeature(Arrays.asList(point));
        if (broadcastPositions) {
            CcuEvent event = new CcuEvent();
            event.setType(CcuEvent.TYPE.MAP_FEATURE_ADDED);
            event.setId(id);
            event.setArg(feature);
            this.getConsole().getImcMsgManager().broadcastToCCUs(event);
        }

        NeptusLog.pub().info("placeLocationOnMap: " + id + " - " + loc);
        return id;
    }

    // Print Image to JPanel
    @Override
    protected void paintComponent(Graphics g) {
        boolean warn = false;
        if (refreshTemp && onScreenImage != null) {
            g.drawImage(onScreenImage, 0, 0, this);
            refreshTemp = false;
        }
        else if (onScreenImageLastGood != null && onScreenImageLastGood.getWidth() == size.width
                && onScreenImageLastGood.getHeight() == size.height) {
            g.drawImage(onScreenImageLastGood, 0, 0, this);
            warn = true;
        }
        else {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, (int) size.width, (int) size.height);
        }

        if (ipCam) {
            String text = fieldName.getText();
            Rectangle2D bounds = g.getFontMetrics().getStringBounds(text, g);
            streamNameJLabel.setText(text);
            streamNameJLabel.setSize((int) size.width, (int) bounds.getHeight() + 5);
            streamNameJLabel.paint(g);

            if (warn) {
                String textWarn = "⚠";
                streamWarnJLabel.setText(textWarn);
                streamWarnJLabel.setSize((int) size.width, (int) size.height);
                streamWarnJLabel.paint(g);
            }
        }
    }

    private void showImage(BufferedImage image) {
        if (!paused) {
            if (onScreenImage != null) {
                onScreenImageLastGood = onScreenImage;
            }

            onScreenImage = image;
        }
        refreshTemp = true;
        repaint();
    }

    // Config Layout
    private void configLayout() {
        // Create Buffer (type MAT) for Image resize
        matResize = new Mat(heightConsole, widthConsole, CvType.CV_8UC3);

        // Config JFrame zoom img
        zoomImg.setSize(300, 300);
        popupZoom = new JPopupMenu();
        popupZoom.setSize(300, 300);

        logDir = String.format(BASE_FOLDER_FOR_IMAGES + "/%s", date.toString().replace(":", "-"));

        // JPanel for info and config values
        config = new JPanel(new MigLayout());

        // JLabel info Data received
        txtText = new JLabel();
        txtText.setToolTipText(I18n.text("Info of Frame Received"));
        info = "Img info";
        txtText.setText(info);
        config.add(txtText, "cell 0 4 3 1, wrap");

        // JLabel info
        txtData = new JLabel();
        txtData.setToolTipText(I18n.text("Info of GPS Received over IMC"));
        info = "GPS IMC";
        txtData.setText(info);
        config.add(txtData, "cell 0 6 3 1, wrap");

        menu = new JDialog(getConsole(), I18n.text("Menu Config"));
        menu.setResizable(false);
        menu.setModalityType(ModalityType.DOCUMENT_MODAL);
        menu.setSize(450, 200);
        menu.setLocationRelativeTo(VideoStream.this);
        menu.setVisible(show_menu);
        ImageIcon imgMenu = ImageUtils.createImageIcon("images/menus/configure.png");
        menu.setIconImage(imgMenu.getImage());
        menu.add(config);
    }

    /*
     * (non-Javadoc)
     *
     * @see pt.lsts.neptus.console.ConsolePanel#cleanSubPanel()
     */
    @Override
    public void cleanSubPanel() {
        closingPanel = true;
    }

    /*
     * (non-Javadoc)
     *
     * @see pt.lsts.neptus.console.ConsolePanel#initSubPanel()
     */
    @Override
    public void initSubPanel() {
        if (findOpenCV()) {
            getConsole().getImcMsgManager().addListener(this);
            configLayout();
            createIPUrl = createFile();
            createIPUrl.start();
            updater = updaterThread();
            updater.start();
            saveImg = updaterThreadSave();
            saveImg.start();
        }
        else {
            NeptusLog.pub().error("Opencv not found.");
            closingPanel = true;
            return;
        }
        setMainVehicle(getConsole().getMainSystem());
    }

    private Thread createFile() {
        Thread ipUrl = new Thread("Create file IPUrl Thread") {
            @Override
            public void run() {
                String iniRsrcPath = FileUtil.getResourceAsFileKeepName(BASE_FOLDER_FOR_URL_INI);
                File confIni = new File(ConfigFetch.getConfFolder() + "/" + BASE_FOLDER_FOR_URL_INI);
                if (!confIni.exists()) {
                    FileUtil.copyFileToDir(iniRsrcPath, ConfigFetch.getConfFolder());
                }
            }
        };
        ipUrl.setDaemon(true);
        return ipUrl;
    }

    // Find OPENCV JNI in host PC
    private boolean findOpenCV() {
        return SearchOpenCv.searchJni();
    }

    // Get size of image over TCP
    private void initSizeImage() {
        // Width size of image
        try {
            widthImgRec = Integer.parseInt(in.readLine());
        }
        catch (NumberFormatException | IOException e) {
            e.printStackTrace();
        }
        // Height size of image
        try {
            heightImgRec = Integer.parseInt(in.readLine());
        }
        catch (NumberFormatException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        xScale = (float) widthConsole / widthImgRec;
        yScale = (float) heightConsole / heightImgRec;
        // Create Buffer (type MAT) for Image receive
        mat = new Mat(heightImgRec, widthImgRec, CvType.CV_8UC3);
    }

    // Thread to handle data receive
    private Thread updaterThread() {
        final int threadId = threadsIdCounter.incrementAndGet();
        NeptusLog.pub().info("New Video Stream Thread " + threadId);
        Thread ret = new Thread("Video Stream Thread " + threadId) {
            final int tid = threadId;
            final String cid = String.format("%05X-%d", VideoStream.this.hashCode(), tid);
            @Override
            public void interrupt() {
                super.interrupt();
                NeptusLog.pub().error("<<<<< Interrupted tid::" + cid + " >>>>>");
                try {
                    VideoCapture captureOld = capture;
                    if (captureOld != null && captureOld.isOpened()) {
                        captureOld.release();
                        NeptusLog.pub().info("Old capture for tid::" + cid + " successfully released");
                    }
                } catch (Exception | Error e) {
                    NeptusLog.pub().warn("Old capture for tid::" + cid + " error releasing :" + e.getMessage());
                }
            }

            @Override
            public void run() {
                initImage();
                setupWatchDog();
                while (true) {
                    if (tid != threadsIdCounter.get()) {
                        NeptusLog.pub().error("<<<<< Killing numb tid::" + cid + " >>>>>");
                        return;
                    }

                    captureLoopAtomicLongMillis.set(System.currentTimeMillis());
                    if (closingPanel) {
                        state = false;
                        ipCam = false;
                    }
                    else if (ipCam) {
                        if (!state) {
                            try {
                                if (capture != null && capture.isOpened()) {
                                    capture.release();
                                    NeptusLog.pub().info("Old capture for tid::" + cid + " successfully released");
                                }
                            } catch (Exception | Error e) {
                                NeptusLog.pub().warn("Old capture for tid::" + cid + " error releasing :" + e.getMessage());
                            }
                            // Create Buffer (type MAT) for Image receive
                            mat = new Mat(heightImgRec, widthImgRec, CvType.CV_8UC3);
                            capture = new VideoCapture();
                            capture.setExceptionMode(true);
                            try {
                                boolean res = capture.open(camUrl);
                                if (!res) {
                                    capture = null;
                                }
                            } catch (Exception | Error e) {
                                capture = null;
                                NeptusLog.pub().error(e.getMessage());
                            }
                            if (capture != null && capture.isOpened()) {
                                state = true;
                                NeptusLog.pub().info("Video Stream from IPCam is captured - tid::" + cid);
                                startWatchDog();
                                emptyFramesCounter.set(0);
                                isCleanTurnOffCam = false;
                            }
                            else {
                                ipCam = false;
                                NeptusLog.pub().info("Video Stream from IPCam is not captured - tid::" + cid);
                            }
                        }
                        // IPCam Capture
                        else if (ipCam && state) {
                            long startTime = System.currentTimeMillis();
                            isAliveIPCam = false;
                            resetWatchDog(4000);
                            while (watchDog.isAlive() && !isAliveIPCam && capture != null && capture.isOpened()) {
                                try {
                                    capture.read(mat);
                                    isAliveIPCam = true;
                                } catch (Exception | Error e) {
                                    NeptusLog.pub().debug(e.getMessage());
                                }
                            }
                            if (isAliveIPCam) {
                                resetWatchDog(4000);
                            }

                            if (!ipCam) {
                                continue;
                            }

                            long stopTime = System.currentTimeMillis();
                            if ((stopTime - startTime) != 0) {
                                infoSizeStream = String.format("Size(%d x %d) | Scale(%.2f x %.2f) | FPS:%d |\t\t\t",
                                        mat.cols(), mat.rows(), xScale, yScale, (int) (1000 / (stopTime - startTime)));
                            }

                            txtText.setText(infoSizeStream);

                            if (mat.empty()) {
                                NeptusLog.pub().warn(I18n.text("ERROR capturing img of IPCam - tid::" + cid));
                                repaint();
                                emptyFramesCounter.incrementAndGet();
                                continue;
                            }

                            emptyFramesCounter.set(0);

                            xScale = (float) widthConsole / mat.cols();
                            yScale = (float) heightConsole / mat.rows();
                            Imgproc.resize(mat, matResize, size);
                            // Convert Mat to BufferedImage
                            offlineImage = UtilCv.matToBufferedImage(matResize);
                            // Display image in JFrame
                            if (histogramFlag) {
                                if (zoomMask) {
                                    zoomTemp = offlineImage;
                                    getCutImage(UtilCv.histogramCv(zoomTemp), zoomX, zoomY);
                                    popupZoom.setVisible(true);
                                }
                                else {
                                    popupZoom.setVisible(false);
                                }

                                if (saveSnapshot) {
                                    UtilCv.saveSnapshot(UtilCv.addText(UtilCv.histogramCv(offlineImage),
                                                    I18n.text("Histogram - On"), LABEL_WHITE_COLOR,
                                                    offlineImage.getWidth() - 5, 20),
                                            String.format(logDir + "/snapshotImage"));
                                    saveSnapshot = false;
                                }
                                showImage(UtilCv.addText(UtilCv.histogramCv(offlineImage),
                                        I18n.text("Histogram - On"),
                                        LABEL_WHITE_COLOR, offlineImage.getWidth() - 5, 20));
                            }
                            else {
                                if (zoomMask) {
                                    getCutImage(offlineImage, zoomX, zoomY);
                                    popupZoom.setVisible(true);
                                }
                                else {
                                    popupZoom.setVisible(false);
                                }

                                if (saveSnapshot) {
                                    UtilCv.saveSnapshot(offlineImage,
                                            String.format(logDir + "/snapshotImage"));
                                    saveSnapshot = false;
                                }
                                showImage(offlineImage);
                            }
                        }
                    }
                    else {
                        try {
                            TimeUnit.MILLISECONDS.sleep(1000);
                        }
                        catch (InterruptedException e) {
                            NeptusLog.pub().warn("<<<<< Interrupted while sleeping tid::" + cid + " >>>>>");
                        }
                        initImage();
                    }
                    if (closingPanel) {
                        break;
                    }
                }
            }
        };
        ret.setDaemon(true);
        return ret;
    }

    private static File checkExistenceOfFolderForFile(File fx) {
        File p = fx.getParentFile();
        if (!p.exists()) {
            p.mkdirs();
        }

        return fx;
    }

    // Thread to handle save image
    private Thread updaterThreadSave() {
        Thread si = new Thread("Save Image") {
            @Override
            public void run() {
                matSaveImg = new Mat(heightImgRec, widthImgRec, CvType.CV_8UC3);
                boolean stateSetUrl = false;

                while (true) {
                    if (ipCam && !stateSetUrl) {
                        captureSave = new VideoCapture();
                        captureSave.open(camUrl, Videoio.CAP_ANY);
                        if (captureSave.isOpened()) {
                            stateSetUrl = true;
                        }
                    }
                    if (ipCam && stateSetUrl) {
                        if (flagBuffImg == true) {
                            long startTime = System.currentTimeMillis();
                            captureSave.read(matSaveImg);
                            if (!matSaveImg.empty()) {
                                String imageJpeg = null;
                                try {
                                    if (histogramFlag) {
                                        imageJpeg = String.format("%s/imageSave/%d_H.jpeg", logDir, cnt);
                                        outputFile = checkExistenceOfFolderForFile(new File(imageJpeg));
                                        ImageIO.write(UtilCv.histogramCv(UtilCv.matToBufferedImage(matSaveImg)), "jpeg",
                                                outputFile);
                                    }
                                    else {
                                        imageJpeg = String.format("%s/imageSave/%d.jpeg", logDir, cnt);
                                        outputFile = checkExistenceOfFolderForFile(new File(imageJpeg));
                                        ImageIO.write(UtilCv.matToBufferedImage(matSaveImg), "jpeg", outputFile);
                                    }
                                }
                                catch (IOException e) {
                                    e.printStackTrace();
                                }
                                cnt++;
                                long stopTime = System.currentTimeMillis();
                                while ((stopTime - startTime) < (1000 / fps)) {
                                    stopTime = System.currentTimeMillis();
                                }
                            }
                        }
                        else {
                            try {
                                TimeUnit.MILLISECONDS.sleep(100);
                            }
                            catch (InterruptedException e) {
                                NeptusLog.pub().warn("Interrupted save while sleeping");
                            }
                        }
                    }
                    else {
                        try {
                            TimeUnit.MILLISECONDS.sleep(1000);
                        }
                        catch (InterruptedException e) {
                            NeptusLog.pub().warn("Interrupted save while sleeping");
                        }
                    }
                    if (closingPanel) {
                        break;
                    }
                }
            }
        };
        si.setDaemon(true);
        return si;
    }

    @Subscribe
    public void consume(ConsoleEventMainSystemChange evt) {
        setMainVehicle(evt.getCurrent());
    }

    private void setMainVehicle(String vehicle) {
        camFov = null;

        ArrayList<SystemProperty> props = ConfigurationManager.getInstance().getPropertiesByEntity(vehicle, "UAVCamera",
                Visibility.DEVELOPER, Scope.GLOBAL);

        String camModel = "";
        double hAOV = 0, vAOV = 0, camTilt = 0;

        for (SystemProperty p : props) {
            if (p.getName().equals("Onboard Camera")) {
                camModel = "" + p.getValue();
            }
            else if (p.getName().equals("(" + camModel + ") Horizontal AOV")) {
                hAOV = Math.toRadians(Double.valueOf("" + p.getValue()));
            }
            else if (p.getName().equals("(" + camModel + ") Vertical AOV")) {
                vAOV = Math.toRadians(Double.valueOf("" + p.getValue()));
            }
            else if (p.getName().equals("(" + camModel + ") Tilt Angle")) {
                camTilt = Math.PI / 2 + Math.toRadians(Double.valueOf("" + p.getValue()));
            }
        }

        if (!camModel.isEmpty()) {
            camFov = new CameraFOV(hAOV, vAOV);
            camFov.setTilt(camTilt);
            NeptusLog.pub().info("Using " + camModel + " camera with " + Math.toDegrees(hAOV) + " x "
                    + Math.toDegrees(vAOV) + " AOV");
        }
        else {
            NeptusLog.pub().error("Could not load camera FOV");
            getConsole().post(Notification.warning(I18n.text("CameraFOV"), I18n.text("Could not load camera FOV")));
            camFov = CameraFOV.defaultFov();
        }
    }

    // IMC handle
    @Subscribe
    public void consume(EstimatedState msg) {
        if (msg.getSourceName().equals(getMainVehicleId()) && findOpenCV()) {
            if (camFov != null) {
                if (!paused) {
                    camFov.setState(msg);
                }
                if (mouseLoc != null) {
                    EventMouseLookAt lookAt = new EventMouseLookAt(camFov.getLookAt(mouseLoc.getX(), mouseLoc.getY()));
                    getConsole().post(lookAt);
                }
            }
        }
    }

    // Fill cv::Mat image with zeros
    private void initImage() {
        if (!noVideoLogoState) {
            if (ImageUtils.getImage("images/novideo.png") == null) {
                matResize.setTo(black);
                offlineImage = UtilCv.matToBufferedImage(matResize);
            }
            else {
                offlineImage = UtilVideoStream.resizeBufferedImage(
                        ImageUtils.toBufferedImage(ImageUtils.getImage("images/novideo.png")), size);
            }

            if (offlineImage != null) {
                showImage(offlineImage);
                noVideoLogoState = true;
            }
        }
        else {
            offlineImage = UtilVideoStream
                    .resizeBufferedImage(ImageUtils.toBufferedImage(ImageUtils.getImage("images/novideo.png")), size);
            showImage(offlineImage);
        }
    }

    // Zoom in
    private void getCutImage(BufferedImage imageToCut, int w, int h) {
        if (w - 50 <= 0) {
            w = 55;
        }
        if (w + 50 >= imageToCut.getWidth()) {
            w = imageToCut.getWidth() - 55;
        }
        if (h - 50 <= 0) {
            h = 55;
        }
        if (h + 50 >= imageToCut.getHeight()) {
            h = imageToCut.getHeight() - 55;
        }

        if (popupZoom.isShowing()) {
            zoomImgCut = new BufferedImage(100, 100, BufferedImage.TYPE_3BYTE_BGR);
            for (int i = -50; i < 50; i++) {
                for (int j = -50; j < 50; j++) {
                    zoomImgCut.setRGB(i + 50, j + 50, imageToCut.getRGB(w + i, h + j));
                }
            }

            // Create new (blank) image of required (scaled) size
            scaledCutImage = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
            // Paint scaled version of image to new image
            graphics2D = scaledCutImage.createGraphics();
            graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics2D.drawImage(zoomImgCut, 0, 0, 300, 300, null);
            // clean up
            graphics2D.dispose();
            // draw image
            zoomLabel.setIcon(new ImageIcon(scaledCutImage));
            zoomImg.revalidate();
            zoomImg.add(zoomLabel);
        }
    }

    private void setupWatchDog() {
        watchDog = new Thread(new Runnable() {
            @Override
            public void run() {
                endTimeMillis = System.currentTimeMillis() + WATCH_DOG_TIMEOUT_MILLIS;
                virtualEndThread = false;
                while (true) {
                    if (System.currentTimeMillis() > endTimeMillis && !virtualEndThread) {
                        if (!isCleanTurnOffCam) {
                            NeptusLog.pub().error("TIME OUT IPCAM");
                            NeptusLog.pub().info("Closing all Video Stream...");
                            noVideoLogoState = false;
                            state = false;
                            ipCam = false;
                            initImage();
                        }
                        virtualEndThread = true;
                    }
                    else if (virtualEndThread) {
                        try {
                            Thread.sleep(1000);
                        }
                        catch (InterruptedException e) {
                        }
                    }

                    try {
                        Thread.sleep(100);
                    }
                    catch (InterruptedException e) {
                    }
                }
            }
        });
    }

    private void startWatchDog() {
        if (!Objects.equals(watchDog.getState().toString(), "TIMED_WAITING")) {
            watchDog.start();
        }
    }

    private void resetWatchDog(double timeout) {
        endTimeMillis = (long) (System.currentTimeMillis() + timeout);
        virtualEndThread = false;
    }

    @Periodic(millisBetweenUpdates = 1_000)
    public void tick() {
        if(emptyFramesCounter.getAndSet(0) <= MAX_NULL_FRAMES_FOR_RECONNECT) return;

        NeptusLog.pub().warn("Stream connection hanging, re-connecting");
        state = false;
    }

    @Periodic(millisBetweenUpdates = 1_000)
    public void tick2() {
        long timer = captureLoopAtomicLongMillis.get();
        if (timer == -1) return;

        if (System.currentTimeMillis() - timer > WATCH_DOG_LOOP_THREAD_TIMEOUT_MILLIS) {
            captureLoopAtomicLongMillis.set(-1);
            try {
                Thread oldUpdater = updater;
                oldUpdater.interrupt();
            } catch (Exception e) {
                NeptusLog.pub().error(e.getMessage());
            }

            updater = updaterThread();
            updater.start();
        }
    }
}
