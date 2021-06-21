import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

public class Keymaker extends JPanel {
	private static final long serialVersionUID = 3183800444126303273L;
	
	static final int ACCOUNT = 0;
	static final int FORMAT = 1;
	static final int DATE = 2;
	
	
	private static final JFrame frame;
	private static final JPanel mainPanel;
	private static final Path root;
	private static final ImageIcon clipIcon, infoIcon, delIcon;

	private static final Map<Character, char[]> charPool;
	private static Map<String, String> language;
	
	private final JPanel pwInfoList;

	private final long mpwh;
	
	private final Dimension buttonDimension;

	private final JTextField accountIn;
	private final JTextField formatIn;
	private final JComboBox<String> sortingSelection;
	
	private int sorting;
	
	private Map<String, String[]> pwInfo;
	
	private static final Clipboard clipboard;
	
	Keymaker(long mpwh) {
		super();
		setLayout(new BorderLayout());
		this.mpwh = mpwh;
		
		buttonDimension = new Dimension(20,20);
		
		JPanel inputPanel = new JPanel();
		inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));
		
		Dimension labelDimension = new Dimension(100, 20);
		JLabel accountLabel = new JLabel(translate("account"));
		accountLabel.setPreferredSize(labelDimension);
		accountIn = new JTextField(20);
		JLabel formatLabel = new JLabel(translate("format"));
		formatLabel.setPreferredSize(labelDimension);
		formatIn = new JTextField(20);
		JButton submitButton = new JButton(translate("submit"));
		
		JPanel accountPanel = new JPanel();
		accountPanel.add(accountLabel);
		accountPanel.add(accountIn);

		JPanel formatPanel = new JPanel();
		formatPanel.add(formatLabel);
		formatPanel.add(formatIn);
		
		JPanel submitPanel = new JPanel();
		submitPanel.setLayout(new BorderLayout());
		submitPanel.add(submitButton, BorderLayout.CENTER);
		
		inputPanel.add(accountPanel);
		inputPanel.add(formatPanel);
		inputPanel.add(submitPanel);
		
		ActionListener submitListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String account = accountIn.getText();
				String format = formatIn.getText();
				String date = ""+System.currentTimeMillis();
				
				if (pwInfo.containsKey(account) && JOptionPane.showConfirmDialog(
						frame, translate("overwrite_pwi?"), translate("pw_already_exists"), 
						JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
						== JOptionPane.CANCEL_OPTION) return;
				
				if (!validFormat(format)) {
					JOptionPane.showConfirmDialog(frame, translate("invalid_format"), translate("error"), 
							JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);
					return;
				}
				
				accountIn.setText("");
				String res = generate(account, format);
				toClipboard(res);
				pwInfo.put(account, new String[] {account, format, date, date});
				safeSettings();
				updatePwInfoList();
			}
		};
		
		accountIn.addActionListener(submitListener);
		submitButton.addActionListener(submitListener);
		
		pwInfoList = new JPanel();
		pwInfoList.setLayout(new BoxLayout(pwInfoList, BoxLayout.Y_AXIS));
		JPanel infoPanel = new JPanel();
		infoPanel.setLayout(new BorderLayout());
		infoPanel.add(pwInfoList, BorderLayout.NORTH);
		infoPanel.add(new JPanel(), BorderLayout.CENTER);
		
		JScrollPane listScroller = new JScrollPane(infoPanel);
		listScroller.setPreferredSize(new Dimension(319, 200));
		listScroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		listScroller.getVerticalScrollBar().setUnitIncrement(5);
		
		add(inputPanel, BorderLayout.NORTH);
		add(listScroller, BorderLayout.CENTER);

		sortingSelection = new JComboBox<String>(new String[] {
				translate("sort_lru"),
				translate("sort_newest"),
				translate("sort_oldest"),
				translate("sort_alphabetically")
		});
		sortingSelection.setSelectedIndex(sorting);
		sortingSelection.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				safeSettings();
				updatePwInfoList();
			}
		});
		
		readSettings();
		updatePwInfoList();
	}
	
	private String settingsName() {
		return "settings/"+ (new MyRandom(mpwh).skip(5) & 0xFFFF) +".ser";
	}
	
	@SuppressWarnings("unchecked")
	private void readSettings() {
		try {
			FileInputStream fIn = new FileInputStream(getFile(settingsName()));
			ObjectInputStream oIn = new ObjectInputStream(fIn);
			
			pwInfo = (Map<String, String[]>) oIn.readObject();
			formatIn.setText(oIn.readUTF());
			sorting = oIn.readInt();
			
			oIn.close();
			fIn.close();
			
		} catch (FileNotFoundException e) {
			JOptionPane.showConfirmDialog(frame, translate("unknown_mpw"), translate("warning"), 
					JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE);
			
			// default values
			pwInfo = new HashMap<String, String[]>();
			formatIn.setText("XXXX-XXXX-XXXX-XXXX");
			sorting = 0;
			
		} catch (Exception e) {
			logException(e);
			JOptionPane.showConfirmDialog(frame, translate("io_error"), 
					translate("error"), JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);
		}
	}
	
	private void safeSettings(){
		try {
			FileOutputStream fOut = new FileOutputStream(getFile(settingsName()));
			ObjectOutputStream oOut = new ObjectOutputStream(fOut);
			
			oOut.writeObject(pwInfo);
			oOut.writeUTF(formatIn.getText());
			oOut.writeInt(sorting);
			oOut.close();
			fOut.close();
		} catch (Exception e) {
			logException(e);
			JOptionPane.showConfirmDialog(frame, translate("io_error"), 
					translate("error"), JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);
		}
	}
	
	private void updatePwInfoList() {
		List<String[]> pwis = new ArrayList<String[]>();
		pwis.addAll(pwInfo.values());
		pwis.sort(pwiComparator());
		pwInfoList.removeAll();
		pwInfoList.add(sortingSelection);
		for (String[] pwi : pwis) pwInfoList.add(pwiContainer(pwi));
		revalidate();
	}
	
	private Comparator<String[]> pwiComparator() {
		switch(sortingSelection.getSelectedIndex()) {
		case 0: return new Comparator<String[]>() {
			@Override
			public int compare(String[] o1, String[] o2) {
				return (int) (Long.parseLong(o2[3]) - Long.parseLong(o1[3]));
			}
		};
		case 1: return new Comparator<String[]>() {
			@Override
			public int compare(String[] o1, String[] o2) {
				return (int) (Long.parseLong(o2[2]) - Long.parseLong(o1[2]));
			}
		};
		case 2: return new Comparator<String[]>() {
			@Override
			public int compare(String[] o1, String[] o2) {
				return (int) (Long.parseLong(o1[2]) - Long.parseLong(o2[2]));
			}
		};
		case 3: return new Comparator<String[]>() {
			@Override
			public int compare(String[] o1, String[] o2) {
				return o1[0].compareToIgnoreCase(o2[0]);
			}
		};
		}
		return null;
	}
	
	private boolean validFormat(String format) {
		for (char c : format.toCharArray()) if (!charPool.containsKey(c)) return false;
		return true;
	}
	
	private String generate(String account, String format) {
		long sh = hash(account.toCharArray());
		long seed = (mpwh ^ sh) + (mpwh * sh);
		MyRandom r = new MyRandom(seed);

		String res = "";
		for (char c : format.toCharArray()) {
			char[] pool = charPool.get(c);
			res += pool[r.nextIndex(pool.length)];
		}
		return res;
	}
	
	private JPanel pwiContainer(String[] pwi) {
		if (pwi == null || pwi.length != 4) throw new IllegalArgumentException();
		
		JPanel container = new JPanel();
		
		container.setLayout(new BorderLayout());
		JPanel labelSide = new JPanel();
		JPanel buttonSide = new JPanel();
		
		JButton clipButton = new JButton();
		clipButton.setPreferredSize(buttonDimension);
		clipButton.setIcon(clipIcon);
		
		JButton infoButton = new JButton();
		infoButton.setPreferredSize(buttonDimension);
		infoButton.setIcon(infoIcon);
		
		JButton deleteButton = new JButton();
		deleteButton.setPreferredSize(buttonDimension);
		deleteButton.setIcon(delIcon);
		
		labelSide.add(new JLabel(pwi[ACCOUNT]));
		buttonSide.add(clipButton);
		buttonSide.add(infoButton);
		buttonSide.add(deleteButton);

		container.add(labelSide, BorderLayout.WEST);
		container.add(buttonSide, BorderLayout.EAST);
		
		clipButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				toClipboard(generate(pwi[ACCOUNT], pwi[FORMAT]));
				pwi[3] = ""+System.currentTimeMillis();
				pwInfo.put(pwi[0], pwi);
			}
		});
		
		infoButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				pwi[3] = ""+System.currentTimeMillis();
				pwInfo.put(pwi[0], pwi);
				JOptionPane.showConfirmDialog(frame, pwiDesc(pwi), translate("pw_info"), 
						JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE);
			}
		});
		
		deleteButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				pwInfo.remove(pwi[ACCOUNT]);
				safeSettings();
				updatePwInfoList();
			}
		});
		
		return container;
	}
	
	private String pwiDesc(String[] pwi) {
		return 
				  translate("account")	+ ": \t" + pwi[ACCOUNT] + System.lineSeparator()
				+ translate("format")	+ ": \t" + pwi[FORMAT] + System.lineSeparator()
				+ translate("password")	+ ": \t" + generate(pwi[ACCOUNT], pwi[FORMAT]) + System.lineSeparator()
				+ translate("created")	+ ": \t" + new Date(Long.parseLong(pwi[DATE])).toString();
	}
	
	@SuppressWarnings("unchecked")
	private static void readLanguage() {
		try {
			FileInputStream fIn = new FileInputStream(getFile("settings/language.ser"));
			ObjectInputStream oIn = new ObjectInputStream(fIn);
			language = (Map<String, String>) oIn.readObject();
			oIn.close();
			fIn.close();
		} catch (Exception e) {
			logException(e);
			JOptionPane.showConfirmDialog(frame, translate("io_error"), 
					translate("error"), JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);
			language = new HashMap<String, String>();
		}
	}
	
	private static JMenuBar createMenu() {
		JMenuBar mb = new JMenuBar();
		
		JMenu languageMenu = new JMenu(translate("language"));
		
		File[] languageFiles = getFile("language").listFiles();
		for (File file : languageFiles) {
			String filename = file.getName();
			String languageName = filename.replaceAll(".ser", "");
			JMenuItem languageSelect = new JMenuItem(languageName);
			languageMenu.add(languageSelect);
			languageSelect.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					try {
						Files.copy(file.toPath(), getFile("settings/language.ser").toPath(), 
								StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e1) {
						logException(e1);
						JOptionPane.showConfirmDialog(frame, translate("io_error"), 
								translate("error"), JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);
					}
					JOptionPane.showConfirmDialog(frame, translate("change_needs_restart"), 
								translate("notification"), JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE);
				}
			});
		}

		JMenu infoMenu = new JMenu(translate("info"));
		
		JMenuItem formatInfo = new JMenuItem(translate("format_info"));
		infoMenu.add(formatInfo);
		formatInfo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JOptionPane.showConfirmDialog(frame, translate("format_info_text"), translate("format_info"), 
						JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE);
			}
		});

		JMenuItem aboutInfo = new JMenuItem(translate("about_info"));
		infoMenu.add(aboutInfo);
		aboutInfo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JOptionPane.showConfirmDialog(frame, translate("about_info_text"), translate("about_info"), 
						JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE);
			}
		});
		
		JMenuItem logout = new JMenuItem(translate("logout"));
		logout.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				login();
			}
		});
		
		mb.add(languageMenu);
		mb.add(infoMenu);
		mb.add(logout);
		
		return mb;
	}
	
	private static void logException(Exception ex) {
		try {
			File log = getFile("err_log/"+System.currentTimeMillis()+".err");
			PrintStream out = new PrintStream(log);
			ex.printStackTrace(out);
			out.close();
			
		} catch (FileNotFoundException e) {
			JOptionPane.showConfirmDialog(frame, translate("err_log_error"), 
					translate("error"), JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);
		}
	}

	private static File getFile(String name) {
		return root.resolve(name).toFile();
	}

	private static BufferedImage getImage(String name) {
		try {
			return ImageIO.read(getFile(name));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private static void toClipboard(String s) {
		clipboard.setContents(new StringSelection(s), null);
	}
	
	private static long hash(char[] str) {
		long h = 0;
		for (char c : str) h = 31*h +c;
		return h;
	}
	
	private static String translate(String s) {
		String t = language.get(s);
		if (t == null) {
			logException(new Exception(s+" not translated"));
			return s;
		}
		return t;
	}
	
	private static long fetchMPW() {
		JPanel panel = new JPanel();
		JLabel label = new JLabel(translate("mpw"));
		JPasswordField mpwIn = new JPasswordField(30);
		panel.add(label);
		panel.add(mpwIn);
		String[] options = new String[]{translate("ok")};
		JOptionPane.showOptionDialog(frame, panel, "Keymaker",
		                         JOptionPane.NO_OPTION, JOptionPane.PLAIN_MESSAGE,
		                         null, options, options[0]);
		return hash(mpwIn.getPassword());
	}
	
	static {

		clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		
		root = Path.of(System.getenv("LOCALAPPDATA"), "Keymaker");
		//TODO installation program
		//err_log.toFile().mkdirs();

		readLanguage();
		
		
		clipIcon = new ImageIcon(getImage("image/clip.png"), null);
		infoIcon = new ImageIcon(getImage("image/info.png"), null);
		delIcon = new ImageIcon(getImage("image/trash.png"), null);
		
		mainPanel = new JPanel();
		
		frame = new JFrame("Keymaker");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setJMenuBar(createMenu());
		frame.setLocationRelativeTo(null);
		frame.add(mainPanel);

		charPool = new HashMap<Character, char[]>();
		charPool.put('-', "-".toCharArray());
		charPool.put('0', "0123456789".toCharArray());
		charPool.put('a', "abcdefghijklmnopqrstuvwxyz".toCharArray());
		charPool.put('A', "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray());
		charPool.put('x', "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray());
		charPool.put('X', "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray());
		charPool.put('$', "!#$%&'()+,-.;=@[]^_`{}~¡¢£¤¥¦§¨©ª«¬­®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ".toCharArray());
		charPool.put('?', ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"+
				"!#$%&'()+,-.;=@[]^_`{}~¡¢£¤¥¦§¨©ª«¬­®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ").toCharArray());
		
	}
	
	public static void login() {
		frame.setVisible(false);
		mainPanel.removeAll();
		mainPanel.add(new Keymaker(fetchMPW()));
		frame.pack();
		frame.setVisible(true);
	}

	public static void main(String[] args) {
		login();
		
		
		Map<String, String> en = new HashMap<String, String>();
		Map<String, String> de = new HashMap<String, String>();
		
		en.put("language", 				"Language");
		en.put("info", 					"Info");
		en.put("logout", 				"Log out");
		en.put("mpw", 					"Master Password");
		en.put("ok", 					"OK");
		en.put("account", 				"Account");
		en.put("format", 				"Format");
		en.put("submit", 				"Submit");
		en.put("format_info", 			"About formats");
		en.put("about_info", 			"About this program");
		en.put("password", 				"Password");
		en.put("created", 				"Created");
		en.put("pw_info", 				"Password Info");
		en.put("error", 				"Error");
		en.put("warning", 				"Warning");
		en.put("notification", 			"Notification");
		en.put("io_error", 				"File Error");
		en.put("err_log_error", 		"Cannot log errors!");
		en.put("sort_lru", 				"Sort by date: recently used");
		en.put("sort_newest", 			"Sort by date: newest");
		en.put("sort_oldest", 			"Sort by date: oldest");
		en.put("sort_alphabetically", 	"Sort alphabetically");
		en.put("change_needs_restart", 	"Keymaker needs to restart in order to apply these changes.");
		en.put("pw_already_exists", 	"Password already exists");
		en.put("overwrite_pwi?", 		"Do you want to overwrite this password info?");
		en.put("invalid_format", 		"Invalid format");
		en.put("unknown_mpw", 			"Unknown master password");
		en.put("format_info_text", 		"You can customize your password's format using wildcard characters:" + System.lineSeparator()
										+ "0 : represents a digit" + System.lineSeparator()
										+ "a : represents a lowercase letter" + System.lineSeparator()
										+ "A : represents an uppercase letter" + System.lineSeparator()
										+ "x : represents a lowercase or uppercase letter" + System.lineSeparator()
										+ "X : represents a lowercase or uppercase letter or a digit" + System.lineSeparator()
										+ "$ : represents a special sign" + System.lineSeparator()
										+ "? : represents any character" + System.lineSeparator()
										+ "- : will remain a hyphen");
		en.put("about_info_text", 		"This program was written by Florian Herzog. " + System.lineSeparator()
										+ "Keymaker generates passwords through hashing and pseudorandoms. " + System.lineSeparator()
										+ "These techniques allow Keymaker to produce high-security passwords " + System.lineSeparator()
										+ "needing only an account name and a master password. " + System.lineSeparator()
										+ "Keymaker will never store any password, but instead " + System.lineSeparator()
										+ "reproduce any password through the master password. " + System.lineSeparator()
										+ "The master password is not stored either. " + System.lineSeparator()
										+ "For questions please write an e-mail to fh.mail@bluewin.ch");

		de.put("language", 				"Sprache");
		de.put("info", 					"Info");
		de.put("logout", 				"Ausloggen");
		de.put("mpw", 					"Master Passwort");
		de.put("ok", 					"OK");
		de.put("account", 				"Konto");
		de.put("format", 				"Format");
		de.put("submit", 				"Erstellen");
		de.put("format_info", 			"Über Formate");
		de.put("about_info", 			"Über dieses Programm");
		de.put("password", 				"Passwort");
		de.put("created", 				"Erstellt");
		de.put("pw_info", 				"Passwort Info");
		de.put("error", 				"Fehler");
		de.put("warning", 				"Warnung");
		de.put("notification", 			"Mitteilung");
		de.put("io_error", 				"Dateifehler");
		de.put("err_log_error", 		"Kann Fehler nicht protokollieren!");
		de.put("sort_lru", 				"Nach Datum sortieren: zuletzt benutzt");
		de.put("sort_newest", 			"Nach Datum sortieren: neuste");
		de.put("sort_oldest", 			"Nach Datum sortieren: älteste");
		de.put("sort_alphabetically", 	"Alphabetisch sortieren");
		de.put("change_needs_restart", 	"Keymaker muss neu gestartet werden damit diese Änderungen aktiv werden.");
		de.put("pw_already_exists", 	"Passwort existiert schon");
		de.put("overwrite_pwi?", 		"Möchtest du die Informationen zu diesem Passwort überschreiben?");
		de.put("invalid_format", 		"Ungültiges Format");
		de.put("unknown_mpw", 			"Unbekanntes Master Passwort");
		de.put("format_info_text", 		"Mithilfe von Platzhalter-Zeichen kannst du das Format eines Passworts vorgeben:" + System.lineSeparator()
										+ "0 : steht für eine Ziffer" + System.lineSeparator()
										+ "a : steht für einen Kleinbuchstaben" + System.lineSeparator()
										+ "A : steht für einen Grossbuchstaben" + System.lineSeparator()
										+ "x : steht für irgendeinen Buchstaben" + System.lineSeparator()
										+ "X : steht für einen Buchstaben oder eine Ziffer" + System.lineSeparator()
										+ "$ : steht für ein Sonderzeichen" + System.lineSeparator()
										+ "? : steht für irgendein Zeichen" + System.lineSeparator()
										+ "- : wird ein Bindestrich bleiben");
		de.put("about_info_text", 		"Dieses Programm wurde geschrieben von Florian Herzog. " + System.lineSeparator()
										+ "Keymaker generiert Passwörter mithilfe von Hashing und Pseudozufallsgeneratoren. " + System.lineSeparator()
										+ "Diese Techniken erlauben Keymaker das Erstellen von Hochsicherheitspasswörtern " + System.lineSeparator()
										+ "durch die Angabe eines Kontonamens und ein Master Passwort. " + System.lineSeparator()
										+ "Keymaker wird nie ein Passwort abspeichern, sondern " + System.lineSeparator()
										+ "Passwörter durch das Master Passwort wiederherstellen. " + System.lineSeparator()
										+ "Auch das Master Passwort wird nicht abgespeichert. " + System.lineSeparator()
										+ "Fragen sind willkommen: fh.mail@bluewin.ch");
		

		try {
			FileOutputStream fOut = new FileOutputStream(getFile("language/English.ser"));
			ObjectOutputStream oOut = new ObjectOutputStream(fOut);
			
			oOut.writeObject(en);
			
			oOut.close();
			fOut.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			FileOutputStream fOut = new FileOutputStream(getFile("language/Deutsch.ser"));
			ObjectOutputStream oOut = new ObjectOutputStream(fOut);
			
			oOut.writeObject(de);
			
			oOut.close();
			fOut.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	
	}
	
	
}
