import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class AndroidRecorderMain extends JFrame
{
    private static final long serialVersionUID           = 1L;

    static final String APP_NAME = "Android Recorder";
    static final String VERSION                    = "1.0";
    final String              COMBO_ANDROID              = "Android          ";
    final String              COMBO_IOS                  = "ios";
    final String              COMBO_CUSTOM_COMMAND       = "custom command";
    final String              ANDROID_MAKE_DIR = " shell mkdir /sdcard/recorder/";
    final String              ANDROID_SCREEN_CAPTURE = " shell screencap -p /sdcard/recorder/";
    final String              ANDROID_SCREEN_RECORD = " shell screenrecord /sdcard/recorder/";
    final String              ANDROID_SCREEN_SIZE = " shell wm size";
    final String              ANDROID_GET_RECORD = "  pull /sdcard/recorder/";
    final String              ANDROID_REMOVE_RECORD = "   shell rm -r /sdcard/recorder/";
    static String CURRENT_DIRECTORY = "";
    String              ANDROID_SELECTED_CMD_FIRST = CURRENT_DIRECTORY + "adb -s ";
    String[]            DEVICES_CMD                = {CURRENT_DIRECTORY + "adb devices", "", ""};
    final String              SCREEN_RECORD = "Screen Record";
    final String              STOP_RECORD = "Stop Record";

    String m_fileName = "";

    static final int          DEFAULT_WIDTH              = 640;
    static final int          DEFAULT_HEIGHT             = 240;
    static final int          MIN_WIDTH                  = 600;
    static final int          MIN_HEIGHT                 = 180;

    static final int          DEVICES_CUSTOM             = 2;

    static final int          STATUS_READY               = 4;

    //Device
    JButton                   m_btnDevice;
    JList                     m_lDeviceList;
    JComboBox                 m_comboDeviceCmd;
    JComboBox                 m_comboCmd;



    JButton m_btnScreenCapture;
    JButton m_btnScreenRecord;
    JButton m_btnRecordSetting;

    String                    m_strSelectedDevice;
    Process                   m_Process;
    Thread                    m_thProcess;
    Thread                    m_thWatchFile;
    Thread                    m_thFilterParse;
    boolean                   m_bPauseADB;

    Object                    FILE_LOCK;
    Object                    FILTER_LOCK;
    volatile int              m_nChangedFilter;
    int                       m_nWinWidth  = DEFAULT_WIDTH;
    int                       m_nWinHeight = DEFAULT_HEIGHT;
    int                       m_nWindState;
    SimpleDateFormat          m_dateFormat;
    static AndroidRecorderMain mainFrame;

    String m_deviceWidth;
    String m_deviceHeight;
    JPanel m_jpSettings;
    JTextField m_tfBitRate;


    public static void main(final String args[]) {
        mainFrame = new AndroidRecorderMain();
        mainFrame.setTitle(APP_NAME + " " + VERSION);
    }

    void exit()
    {
        if(m_Process != null) m_Process.destroy();
        if(m_thProcess != null) m_thProcess.interrupt();
        if(m_thWatchFile != null) m_thWatchFile.interrupt();
        if(m_thFilterParse != null) m_thFilterParse.interrupt();

        System.exit(0);
    }

    /**
     * @throws HeadlessException
     */
    public AndroidRecorderMain()
    {
        super();
        addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e)
            {
                exit();
            }
        });
        initValue();

        Container pane = getContentPane();
        pane.setLayout(new BorderLayout());

        pane.add(getOptionPanel(), BorderLayout.WEST);

        setVisible(true);
        loadCmd();

        setSize(m_nWinWidth, m_nWinHeight);
        setExtendedState( m_nWindState );
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
    }

    final String INI_FILE_CMD       = "LogFilterCmd.ini";
    final String INI_CMD_COUNT      = "CMD_COUNT";
    final String INI_CMD            = "CMD_";


    void loadCmd()
    {
        try
        {
            Properties p = new Properties();

            // ini ÆÄÀÏ ÀÐ±â
            p.load(new FileInputStream(INI_FILE_CMD));

            int nCount = Integer.parseInt(p.getProperty(INI_CMD_COUNT));
            for(int nIndex = 0; nIndex < nCount; nIndex++)
            {
                m_comboCmd.addItem(p.getProperty(INI_CMD + nIndex));
            }
        }
        catch(Exception e)
        {
            System.out.println(e);
        }
    }

    Component getCmdPanel()
    {
        JPanel jpOptionDevice = new JPanel();
        jpOptionDevice.setBorder(BorderFactory.createTitledBorder("Device select"));
        jpOptionDevice.setLayout(new BorderLayout());

        JPanel jpCmd = new JPanel();
        m_comboDeviceCmd = new JComboBox();
        m_comboDeviceCmd.addItem(COMBO_ANDROID);
        m_comboDeviceCmd.addItemListener(new ItemListener()
        {
            public void itemStateChanged(ItemEvent e)
            {
                if(e.getStateChange() != ItemEvent.SELECTED) return;

                DefaultListModel listModel = (DefaultListModel)m_lDeviceList.getModel();
                listModel.clear();
                if (e.getItem().equals(COMBO_CUSTOM_COMMAND)) {
                    m_comboDeviceCmd.setEditable(true);
                } else {
                    m_comboDeviceCmd.setEditable(false);
                }

            }
        });

        final DefaultListModel listModel = new DefaultListModel();
        m_btnDevice = new JButton("OK");
        m_btnDevice.setMargin(new Insets(0, 0, 0, 0));
        m_btnDevice.addActionListener(m_alButtonListener);

        jpCmd.add(m_comboDeviceCmd);
        jpCmd.add(m_btnDevice);

        jpOptionDevice.add(jpCmd, BorderLayout.NORTH);

        m_lDeviceList = new JList(listModel);
        JScrollPane vbar = new JScrollPane(m_lDeviceList);
        vbar.setPreferredSize(new Dimension(100,50));
        m_lDeviceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        m_lDeviceList.addListSelectionListener(new ListSelectionListener()
        {
            public void valueChanged(ListSelectionEvent e)
            {
                JList deviceList = (JList)e.getSource();
                Object selectedItem = (Object)deviceList.getSelectedValue();
                m_strSelectedDevice = "";
                if(selectedItem != null)
                {
                    m_strSelectedDevice = selectedItem.toString();
                    m_strSelectedDevice = m_strSelectedDevice.replace("\t", " ").replace("device", "").replace("offline", "");
                    getScreenSize();
                }
            }
        });
        jpOptionDevice.add(vbar);

        return jpOptionDevice;
    }

    void getScreenSize() {
        String cmd = ANDROID_SELECTED_CMD_FIRST + m_strSelectedDevice + ANDROID_SCREEN_SIZE;
        try {

            Process oProcess = Runtime.getRuntime().exec(cmd);

            String s;
            BufferedReader stdOut = new BufferedReader(new InputStreamReader(oProcess.getInputStream()));
            while ((s = stdOut.readLine()) != null) {
                if (s.contains("Physical size")) {
                    String size = s.replaceAll(".+ : ", "");
                    System.out.println(size);
                    String[] sizes = size.split("x");
                    if (sizes.length != 2)
                        return;
                    m_deviceWidth = sizes[0];
                    m_deviceHeight = sizes[1];
                }
            }
        } catch (Exception e) {

        }
    }

    Component getActionPanel()
    {
        JPanel jpMain = new JPanel(new BorderLayout());

        JPanel jpLogFilter = new JPanel();
        jpLogFilter.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        jpLogFilter.setBorder(BorderFactory.createTitledBorder("Capture"));
        m_btnScreenCapture = new JButton("Screen Capture");
        m_btnScreenCapture.setMargin(new Insets(0, 0, 0, 0));
        m_btnScreenRecord = new JButton(SCREEN_RECORD);
        m_btnScreenRecord.setMargin(new Insets(0, 0, 0, 0));
        ImageIcon normalIcon = new ImageIcon("icon/icon_setup.png");
        m_btnRecordSetting = new JButton(normalIcon);
        m_btnRecordSetting.setOpaque(false);
        m_btnRecordSetting.setContentAreaFilled(false);
        m_btnRecordSetting.setBorderPainted(false);
        m_btnRecordSetting.setMargin(new Insets(0, 0, 0, 0));
        m_btnScreenCapture.addActionListener(m_alButtonListener);
        m_btnScreenRecord.addActionListener(m_alButtonListener);
        m_btnRecordSetting.addActionListener(m_alButtonListener);
        jpLogFilter.add(m_btnScreenCapture);
        jpLogFilter.add(m_btnScreenRecord);
        jpLogFilter.add(m_btnRecordSetting);
        jpMain.add(jpLogFilter, BorderLayout.NORTH);

        JPanel jpGuide = new JPanel();
        jpGuide.setLayout(new BoxLayout(jpGuide, BoxLayout.Y_AXIS));

        m_jpSettings = new JPanel();
        m_jpSettings.setLayout(new BoxLayout(m_jpSettings, BoxLayout.Y_AXIS));
        // bit rate
        JPanel jpBitRate = new JPanel();
        jpBitRate.setAlignmentX( Component.LEFT_ALIGNMENT );
        JLabel bitRate = new JLabel("Bit Rate : " );
        jpBitRate.add(bitRate);
        m_tfBitRate = new JTextField();
        m_tfBitRate.setText("4");
        jpBitRate.add(m_tfBitRate);
        JLabel bitRateUnit = new JLabel(" Mbps  " );
        jpBitRate.add(bitRateUnit);

        // size
        JPanel jpScreenSize = new JPanel();
        jpScreenSize.setAlignmentX( Component.LEFT_ALIGNMENT );
        JLabel lWidth = new JLabel("Width : ");
        jpScreenSize.add(lWidth);


        m_jpSettings.add(jpBitRate);
        m_jpSettings.add(jpScreenSize);

        JPanel jpNotes = new JPanel();
        JTextArea note1 = new JTextArea("");
        note1.setText("* 일부 단말은 장치의 네이티브 해상도로 녹화가 불가능합니다.\r\n" +
                "  : 이상발생시 해상도를 낮춰서 재시도 해보세요.\r\n" +
                "* 녹화중 화면의 회전은 지원하지 않습니다.\r\n" +
                "  : 녹화중 회전 시, 녹화된 화면 일부가 잘릴 수 있습니다.\r\n" +
                "* 오디오는 녹음되지 않습니다.");
        jpNotes.add(note1);

        jpGuide.add(m_jpSettings);
        m_jpSettings.setVisible(false);
        jpGuide.add(jpNotes);

        jpMain.add(jpGuide, BorderLayout.CENTER);
//        일부 단말은 장치의 네이티브 해상도로 녹화가 불가능하다. 만약 화면 녹화시 이상이 발생한다면 해상도를 낮춰서 재시도 해보길 바란다.
//        녹화중 화면의 회전은 지원하지 않는다. 만약 녹화중 화면을 회전한다면, 녹화된 화면의 일부가 잘릴 수 있다.
//            오디오는 녹음되지 않는다.

        return jpMain;
    }

    Component getOptionFilter()
    {
        JPanel optionFilter = new JPanel(new BorderLayout());

        optionFilter.add(getCmdPanel(), BorderLayout.WEST);
        optionFilter.add(getActionPanel(), BorderLayout.EAST);

        return optionFilter;
    }

    Component getOptionPanel()
    {
        JPanel optionMain = new JPanel(new BorderLayout());

        optionMain.add(getOptionFilter(), BorderLayout.CENTER);

        return optionMain;
    }


    void initValue()
    {
        m_dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

        m_bPauseADB         = false;
        FILE_LOCK           = new Object();
        FILTER_LOCK         = new Object();
        m_nChangedFilter    = STATUS_READY;
    }

    void setChangeCommand() {
        ANDROID_SELECTED_CMD_FIRST = CURRENT_DIRECTORY + "adb -s ";
        DEVICES_CMD[0] = CURRENT_DIRECTORY + "adb devices";
    }

    void setDeviceList()
    {
        m_strSelectedDevice = "";

        DefaultListModel listModel = (DefaultListModel)m_lDeviceList.getModel();
        try
        {
            listModel.clear();
            String s;
            String strCommand = DEVICES_CMD[m_comboDeviceCmd.getSelectedIndex()];
            if(m_comboDeviceCmd.getSelectedIndex() == DEVICES_CUSTOM)
                strCommand = (String)m_comboDeviceCmd.getSelectedItem();
            Process oProcess = Runtime.getRuntime().exec(strCommand);

            // ¿ÜºÎ ÇÁ·Î±×·¥ Ãâ·Â ÀÐ±â
            BufferedReader stdOut   = new BufferedReader(new InputStreamReader(oProcess.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(oProcess.getErrorStream()));

            // "Ç¥ÁØ Ãâ·Â"°ú "Ç¥ÁØ ¿¡·¯ Ãâ·Â"À» Ãâ·Â
            while ((s =   stdOut.readLine()) != null)
            {
                if(!s.equals("List of devices attached "))
                {
                    s = s.replace("\t", " ");
                    s = s.replace("device", "");
                    listModel.addElement(s);
                }
            }
            while ((s = stdError.readLine()) != null)
            {
                listModel.addElement(s);
            }

            System.out.println("Exit Code: " + oProcess.exitValue());
        }
        catch(Exception e)
        {
            System.out.println("Exception");
            listModel.addElement(e);
            CURRENT_DIRECTORY = "./";
            setChangeCommand();
            setDeviceList();
        }
    }

    String getMkdirCmd() {
        return ANDROID_SELECTED_CMD_FIRST + m_strSelectedDevice + ANDROID_MAKE_DIR;
    }

    String getCaptureCmd() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        m_fileName = m_dateFormat.format(timestamp) + ".png";
        return ANDROID_SELECTED_CMD_FIRST + m_strSelectedDevice + ANDROID_SCREEN_CAPTURE + m_fileName;
    }

    String getRecordCmd() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        m_fileName = m_dateFormat.format(timestamp) + ".mp4";
        return ANDROID_SELECTED_CMD_FIRST + m_strSelectedDevice + ANDROID_SCREEN_RECORD + m_fileName;
    }

    String getRecordDataCmd(String newFileName) {
        return ANDROID_SELECTED_CMD_FIRST + m_strSelectedDevice + ANDROID_GET_RECORD + m_fileName + " " + newFileName;
    }

    String getRemoveDataCmd() {
        return ANDROID_SELECTED_CMD_FIRST + m_strSelectedDevice + ANDROID_REMOVE_RECORD;
    }

    public void setTitle(String strTitle)
    {
        super.setTitle(strTitle);
    }

    void recordScreen()
    {
        try {
            m_Process = null;

            m_Process = Runtime.getRuntime().exec(getMkdirCmd());
            Thread.sleep(100);
            m_Process = Runtime.getRuntime().exec(getRecordCmd());
            System.out.println("m_Process() = " + m_Process);
            m_btnScreenRecord.setText(STOP_RECORD);
        } catch(Exception e) {
            System.out.println("e = " + e);
        }
    }

    String getDestination() {
        FileDialog fd = new FileDialog(this, "Save as", FileDialog.SAVE);
        fd.setFile(m_fileName);
        fd.setDirectory(".");
        fd.setVisible(true);
        if (fd.getDirectory() == null || fd.getFile() == null)
            return null;
        String saveAsFile = fd.getDirectory() + fd.getFile();
        System.out.println("Save as file: " + saveAsFile);
        return saveAsFile;
    }

    void stopRecord() {
        try {
            if (m_Process != null) {
                m_Process.destroy();
                Thread.sleep(500);
            }
            String newFile = getDestination();
            if (newFile == null)
                return;
            m_Process = Runtime.getRuntime().exec(getRecordDataCmd(newFile));
            Thread.sleep(1000);
            m_Process = Runtime.getRuntime().exec(getRemoveDataCmd());
            m_btnScreenRecord.setText(SCREEN_RECORD);
        } catch(Exception e) {
            System.out.println("e = " + e);
        }
    }

    void captureScreen() {
        try {
            m_Process = null;

            m_Process = Runtime.getRuntime().exec(getMkdirCmd());
            Thread.sleep(500);
            m_Process = Runtime.getRuntime().exec(getCaptureCmd());
            String newFile = getDestination();
            if (newFile == null)
                return;
            m_Process = Runtime.getRuntime().exec(getRecordDataCmd(newFile));
            System.out.println("m_Process() = " + m_Process);
            Thread.sleep(500);
            m_Process = Runtime.getRuntime().exec(getRemoveDataCmd());
            System.out.println("m_Process() = " + m_Process);
        } catch(Exception e) {
            System.out.println("e = " + e);
        }

    }

    ActionListener m_alButtonListener = new ActionListener()
    {
        public void actionPerformed(ActionEvent e)
        {
            if(e.getSource().equals(m_btnDevice)) {
                // test message dialog
//            try {
//                Process oProcess = Runtime.getRuntime().exec(CURRENT_DIRECTORY);
//                BufferedReader stdOut = new BufferedReader(new InputStreamReader(oProcess.getInputStream()));
//                String s;
//                while ((s = stdOut.readLine()) != null) {
////                System.out.println(s);
//                    JOptionPane.showMessageDialog(mainFrame,
//                            s,
//                            "Warning",
//                            JOptionPane.WARNING_MESSAGE);
//                }
//            } catch (Exception excep) {}

                setDeviceList();
            }
            else if(e.getSource().equals(m_btnScreenCapture)) {
                if (m_strSelectedDevice == null || m_strSelectedDevice.length() == 0) {
                    JOptionPane.showMessageDialog(null, "Please select device first.");
                }
                else
                    captureScreen();
            }
            else if(e.getSource().equals(m_btnScreenRecord)) {
                if (m_strSelectedDevice == null || m_strSelectedDevice.length() == 0) {
                    JOptionPane.showMessageDialog(null, "Please select device first.");
                }
                else {
                    if (m_btnScreenRecord.getText().equals(SCREEN_RECORD))
                        recordScreen();
                    else
                        stopRecord();
                }
            }
            else if (e.getSource().equals(m_btnRecordSetting)) {
                m_jpSettings.setVisible(!m_jpSettings.isVisible());
            }
        }
    };


}

