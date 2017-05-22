package widgets;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.StyleConstants;

import chat.Failure;
import chat.Vocabulary;

import models.Message;
import models.NameSetListModel;

public class ClientFrame2 extends AbstractClientFrame
{
	/**
	 * Lecteur de flux d'entrée. Lit les données texte du {@link #inPipe} pour
	 * les afficher dans le {@link #document}
	 */
	private ObjectInputStream inOS;

	/**
	 * Le label indiquant sur quel serveur on est connecté
	 */
	protected final JLabel serverLabel;

	/**
	 * La zone du texte à envoyer
	 */
	protected final JTextField sendTextField;

	/**
	 * Actions à réaliser lorsque l'on veut effacer le contenu du document
	 */
	private final ClearMessagesAction clearMessagesAction;

	/**
	 * Actions à réaliser lorsque l'on veut envoyer un message au serveur
	 */
	private final SendAction sendAction;

	/**
	 * Actions à réaliser lorsque l'on veut envoyer un message au serveur
	 */
	protected final QuitAction quitAction;
	
	protected final FilterSelectedAction filterSelectedAction;
	protected final SortAction sortDateAction;
	protected final SortAction sortContentAction;
	protected final SortAction sortAuthorAction;
	protected final ClearSelectedAction clearSelectedAction;
	protected final KickSelectedUsersAction kickSelectedUsersAction;

	/**
	 * Référence à la fenêtre courante (à utiliser dans les classes internes)
	 */
	protected final JFrame thisRef;

	private JPopupMenu popupMenu;
	private JCheckBoxMenuItem filterMenuItem;
	private JToggleButton filterButton;
	private Vector<Integer> selectedUsers;
	protected Vector<Message> storedMessage;	
	private String nameUser;
	private ListSelectionModel selectionModel;
	
	NameSetListModel userListModel = new NameSetListModel();

	/**
	 * Constructeur de la fenêtre
	 * @param name le nom de l'utilisateur
	 * @param host l'hôte sur lequel on est connecté
	 * @param commonRun état d'exécution des autres threads du client
	 * @param parentLogger le logger parent pour les messages
	 * @throws HeadlessException
	 */
	public ClientFrame2(String name,
										 String host,
										 Boolean commonRun,
										 Logger parentLogger)
					throws HeadlessException
	{
		super(name, host, commonRun, parentLogger);
		thisRef = this;

		storedMessage = new Vector<>();
		selectedUsers = new Vector<>();
		nameUser = name;

		// --------------------------------------------------------------------
		// Flux d'IO
		//---------------------------------------------------------------------
	/*
	 * Attention, la création du flux d'entrée doit (éventuellement) être
	 * reportée jusqu'au lancement du run dans la mesure où le inPipe
	 * peut ne pas encore être connecté à un PipedOutputStream
	 */

		// --------------------------------------------------------------------
		// Création des actions send, clear et quit
		// --------------------------------------------------------------------

		sendAction = new SendAction();
		clearMessagesAction = new ClearMessagesAction();
		quitAction = new QuitAction();

		clearSelectedAction = new ClearSelectedAction();
		kickSelectedUsersAction = new KickSelectedUsersAction();
		filterSelectedAction = new FilterSelectedAction();

		sortDateAction = new SortAction("Date");
		sortContentAction = new SortAction("Content");
		sortAuthorAction = new SortAction("Author");

		addWindowListener(new FrameWindowListener());

		// --------------------------------------------------------------------
		// Widgets setup (handled by Window builder)
		// --------------------------------------------------------------------
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);
		getContentPane().add(toolBar, BorderLayout.NORTH);

		JButton quitButton = new JButton(quitAction);
		quitButton.setHideActionText(true);
		toolBar.add(quitButton);

		JSeparator separator1 = new JSeparator(1);
		toolBar.add(separator1); 

		JButton clearSelectedButton = new JButton(clearSelectedAction);
		clearSelectedButton.setHideActionText(true);
		toolBar.add(clearSelectedButton);

		JButton kickSelectedButton = new JButton(kickSelectedUsersAction);
		kickSelectedButton.setHideActionText(true);
		toolBar.add(kickSelectedButton);

		JSeparator separator2 = new JSeparator(1);
		toolBar.add(separator2); 

		JButton clearButton = new JButton(clearMessagesAction);
		clearButton.setHideActionText(true);
		toolBar.add(clearButton);

		filterButton = new JToggleButton(filterSelectedAction);
		filterButton.setHideActionText(true);
		toolBar.add(filterButton);

		Component toolBarSep = Box.createHorizontalGlue();
		toolBar.add(toolBarSep);

		serverLabel = new JLabel(host == null ? "" : host);
		toolBar.add(serverLabel);


		JPanel sendPanel = new JPanel();
		getContentPane().add(sendPanel, BorderLayout.SOUTH);


		sendPanel.setLayout(new BorderLayout(0, 0));
		sendTextField = new JTextField();
		sendTextField.setAction(sendAction);
		sendPanel.add(sendTextField);
		sendTextField.setColumns(0);

		JButton sendButton = new JButton(sendAction);
		sendButton.setHideActionText(true);
		sendPanel.add(sendButton, BorderLayout.EAST);

		JPanel container = new JPanel();
		getContentPane().add(container, BorderLayout.CENTER);
		container.setLayout(new GridLayout(1, 2));

		JScrollPane scrollPaneUser = new JScrollPane();
		container.add(scrollPaneUser);

		JScrollPane scrollPaneMessage = new JScrollPane();
		container.add(scrollPaneMessage);

		JTextPane textPane = new JTextPane();
		textPane.setEditable(false);
		// autoscroll textPane to bottom
		DefaultCaret caret = (DefaultCaret) textPane.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		scrollPaneMessage.setViewportView(textPane);

		JList<String> userList = new JList<>();
		userList.setModel(userListModel);
		userListModel.add(name);
		userList.setCellRenderer(new ColorTextRenderer());
		scrollPaneUser.setViewportView(userList);

		popupMenu = new JPopupMenu();
		popupMenu.add(clearSelectedAction);
		popupMenu.add(kickSelectedUsersAction);

		MouseListener popupListener = new PopupListener();
		userList.addMouseListener(popupListener);

		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu connectionsMenu = new JMenu("Connections");
		menuBar.add(connectionsMenu);

		JMenu messagesMenu = new JMenu("Messages");
		menuBar.add(messagesMenu);

		JMenu usersMenu = new JMenu("Users");
		menuBar.add(usersMenu);

		JMenuItem quitMenuItem = new JMenuItem(quitAction);
		connectionsMenu.add(quitMenuItem);

		JMenuItem clearMessagesMenuItem = new JMenuItem(clearMessagesAction);
		messagesMenu.add(clearMessagesMenuItem);

		filterMenuItem = new JCheckBoxMenuItem(filterSelectedAction);
		messagesMenu.add(filterMenuItem);

		JMenu sortMenu = new JMenu("Sort");
		messagesMenu.add(sortMenu);
		JMenuItem sortDateMenuItem = new JMenuItem(sortDateAction);
		sortMenu.add(sortDateMenuItem);
		JMenuItem sortContentMenuItem = new JMenuItem(sortContentAction);
		sortMenu.add(sortContentMenuItem);
		JMenuItem sortAuthorMenuItem = new JMenuItem(sortAuthorAction);
		sortMenu.add(sortAuthorMenuItem);

		JMenuItem clearSelectedMenuItem = new JMenuItem(clearSelectedAction);
		usersMenu.add(clearSelectedMenuItem);

		JMenuItem kickSelectedUsersMenuItem = new JMenuItem(kickSelectedUsersAction);
		usersMenu.add(kickSelectedUsersMenuItem);

		filterSelectedAction.setEnabled(false);
		clearSelectedAction.setEnabled(false);
		kickSelectedUsersAction.setEnabled(false);

		// --------------------------------------------------------------------
		// Documents
		// récupération du document du textPane ainsi que du documentStyle et du
		// defaultColor du document
		//---------------------------------------------------------------------
		document = textPane.getStyledDocument();
		documentStyle = textPane.addStyle("New Style", null);
		defaultColor = StyleConstants.getForeground(documentStyle);

		selectionModel = userList.getSelectionModel();
		selectionModel.addListSelectionListener(new ListSelectionListener()
		{
			@Override
			public void valueChanged(ListSelectionEvent lse)
			{
				ListSelectionModel lsm = (ListSelectionModel) lse.getSource();
				
				boolean isAdjusting = lse.getValueIsAdjusting();
				selectedUsers = new Vector<>();

				if (!isAdjusting) {
					if (lsm.isSelectionEmpty()) {
						filterSelectedAction.setEnabled(false);
						clearSelectedAction.setEnabled(false);
						kickSelectedUsersAction.setEnabled(false);
					} else {
						filterSelectedAction.setEnabled(true);
						clearSelectedAction.setEnabled(true);
						kickSelectedUsersAction.setEnabled(true);
						int minSelectionIndex = lsm.getMinSelectionIndex();
						int maxSelectionIndex = lsm.getMaxSelectionIndex();
						
						for (int i = minSelectionIndex; i <= maxSelectionIndex ; ++i)
							if (lsm.isSelectedIndex(i))
								selectedUsers.add(i);
					}
				}
			}
		});
	}

	protected void writerMessage(Message message)
	{
		String author = message.getAuthor();
		
		if ((author != null) && (author.length() > 0))
			StyleConstants.setForeground(documentStyle, new Color(author.hashCode()).darker());
		
		try {
			document.insertString(document.getLength(), message.toString() + Vocabulary.newLine, documentStyle);
		} catch (BadLocationException e) {
			logger.warning("ClientFrame2: bad location");
		}
		
		StyleConstants.setForeground(documentStyle, defaultColor);
	}

	/**
	 * Listener lorsque le bouton #btnClear est activé. Efface le contenu du
	 * {@link #document}
	 */
	protected class ClearMessagesAction extends AbstractAction
	{

		public ClearMessagesAction()
		{
			putValue(SMALL_ICON, new ImageIcon(ClientFrame2.class.getResource("/icons/erase2-16.png")));
			putValue(LARGE_ICON_KEY, new ImageIcon(ClientFrame2.class.getResource("/icons/erase2-32.png")));
			putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.META_MASK));
			putValue(NAME, "Clear Messages");
			putValue(SHORT_DESCRIPTION, "Clear all messages");
		}

		/**
		 * Opérations réalisées lorsque l'action est sollicitée
		 * @param e évènement à l'origine de l'action
		 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
		 */
		@Override
		public void actionPerformed(ActionEvent evt)
		{
			try {
				document.remove(0, document.getLength());
				storedMessage = new Vector<>();
			} catch (BadLocationException e) {
				logger.warning("ClientFrame: bad location");
				logger.warning(e.getLocalizedMessage());
			}
		}
	}
	
	protected class SendAction extends AbstractAction
	{
		public SendAction()
		{
			putValue(SMALL_ICON, new ImageIcon(ClientFrame2.class.getResource("/icons/sent-16.png")));
			putValue(LARGE_ICON_KEY, new ImageIcon(ClientFrame2.class.getResource("/icons/sent-32.png")));
			putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.META_MASK));
			putValue(NAME, "Send");
			putValue(SHORT_DESCRIPTION, "Send text to other clients");
		}

		/**
		 * Opérations réalisées lorsque l'action est sollicitée
		 * @param e évènement à l'origine de l'action
		 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
		 */
		@Override
		public void actionPerformed(ActionEvent evt)
		{
			String content = sendTextField.getText();
			
			if (content != null && content.length() > 0) {
				sendMessage(content);
				sendTextField.setText("");
			}
		}
	}
	
	private class QuitAction extends AbstractAction
	{
		public QuitAction()
		{
				putValue(SMALL_ICON, new ImageIcon(ClientFrame2.class.getResource("/icons/disconnected-16.png")));
				putValue(LARGE_ICON_KEY, new ImageIcon(ClientFrame2.class.getResource("/icons/disconnected-32.png")));
				putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.META_MASK));
				putValue(NAME, "Quit");
				putValue(SHORT_DESCRIPTION, "Send byeCmd and quit the client");
		}

		/**
		 * Opérations réalisées lorsque l'action "quitter" est sollicitée
		 * @param e évènement à l'origine de l'action
		 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
		 */
		@Override
		public void actionPerformed(ActionEvent evt)
		{
			logger.info("QuitAction: sending bye ... ");
			serverLabel.setText("");
			thisRef.validate();

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				return;
			}
			
			sendMessage(Vocabulary.byeCmd);
		}
	}

	private class FilterSelectedAction extends AbstractAction
	{
		public FilterSelectedAction()
		{
			putValue(SMALL_ICON,
							new ImageIcon(ClientFrame2.class
											.getResource("/icons/filled_filter-16.png")));
			putValue(LARGE_ICON_KEY,
							new ImageIcon(ClientFrame2.class
											.getResource("/icons/filled_filter-32.png")));
			putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.META_MASK));
			putValue(NAME, "Filter Messages");
			putValue(SHORT_DESCRIPTION, "Filter messages of the selected users");
		}
		
		@Override
		public void actionPerformed(ActionEvent evt)
		{
			AbstractButton source = (AbstractButton) evt.getSource();

			try {
				document.remove(0, document.getLength());
			} catch (BadLocationException e) {
				logger.warning("ClientFrame: bad location");
				logger.warning(e.getLocalizedMessage());
			}

			Consumer<Message> messagePrinter = (Message message) -> writerMessage(message);

			if (source.isSelected()) {
				filterMenuItem.setSelected(true);
				filterButton.setSelected(true);

				Predicate<Message> selectionFilter = (Message message) ->
				{
					if (message != null && message.hasAuthor() && selectedUsers.contains(userListModel.indexOf(message.getAuthor())))
						return true;
					else
						return false;
				};
				storedMessage.stream().sorted().filter(selectionFilter).forEach(messagePrinter);
			} else {
					filterMenuItem.setSelected(false);
					filterButton.setSelected(false);
					storedMessage.stream().sorted().forEach(messagePrinter);
			}
		}
	}

	private class SortAction extends AbstractAction
	{
		boolean date = true;
		boolean content = true;
		boolean author = true;

		public SortAction(String str)
		{
			putValue(NAME, str);
			putValue(SHORT_DESCRIPTION, "Sort the messages by " + str);
			
			if (str.equals("Date"))
				date = true;
			
			if (str.equals("Content"))
				content = true;
			
			if (str.equals("Author"))
				author = true;
		}
		
		@Override
		public void actionPerformed(ActionEvent evt)
		{
			Consumer<Message> messagePrinter = (Message message) -> writerMessage(message);

			if (date) {
				Message.addOrder(Message.MessageOrder.DATE);
				Message.removeOrder(Message.MessageOrder.CONTENT);
				Message.removeOrder(Message.MessageOrder.AUTHOR);
			} else if (content) {
				Message.removeOrder(Message.MessageOrder.DATE);
				Message.addOrder(Message.MessageOrder.CONTENT);
				Message.removeOrder(Message.MessageOrder.AUTHOR);
			} else if (author) {
				Message.removeOrder(Message.MessageOrder.DATE);
				Message.removeOrder(Message.MessageOrder.CONTENT);
				Message.addOrder(Message.MessageOrder.AUTHOR);
			}
			
			try {
				document.remove(0, document.getLength());
			} catch (BadLocationException e){
				logger.warning("ClientFrame: bad location");
				logger.warning(e.getLocalizedMessage());
			}

			if (filterButton.isSelected())
			{
				Predicate<Message> selectionFilter = (Message message) ->
				{
					if (message != null && message.hasAuthor() && selectedUsers.contains(userListModel.indexOf(message.getAuthor())))
						return true;
					else
						return false;
				};
				storedMessage.stream().sorted().filter(selectionFilter).forEach(messagePrinter);
			} else {
				storedMessage.stream().sorted().forEach(messagePrinter);
			}
		}
	}

	private class ClearSelectedAction extends AbstractAction{
		public ClearSelectedAction()
		{
			putValue(SMALL_ICON, new ImageIcon(ClientFrame2.class.getResource("/icons/delete_database-16.png")));
			putValue(LARGE_ICON_KEY, new ImageIcon(ClientFrame2.class.getResource("/icons/delete_database-32.png")));
			putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_MASK));
			putValue(NAME, "Clear selected");
			putValue(SHORT_DESCRIPTION, "Clear messages of the selected users");
		}
		
		@Override
		public void actionPerformed(ActionEvent evt)
		{
			try {
				document.remove(0, document.getLength());
			} catch (BadLocationException e) {
				logger.warning("ClientFrame2: clear doc: bad location");
				logger.warning(e.getLocalizedMessage());
			}

			Vector<Message> remainingmessage = new Vector<>();

			for (Message message : storedMessage)
				if (message.hasAuthor() && !selectedUsers.contains(userListModel.indexOf(message.getAuthor())))
					remainingmessage.add(message);

			storedMessage = new Vector<>(remainingmessage);

			Consumer<Message> messagePrinter = (Message message) -> writerMessage(message);

			if (filterButton.isSelected()) {
				Predicate<Message> selectionFilter = (Message message) ->
				{
					if (message != null && message.hasAuthor() && selectedUsers.contains(userListModel.indexOf(message.getAuthor())))
						return true;
					else
						return false;
				};

				storedMessage.stream().sorted().filter(selectionFilter).forEach(messagePrinter);
			} else {
				storedMessage.stream().sorted().forEach(messagePrinter);
			}
		}
	}
	
	private class KickSelectedUsersAction extends AbstractAction{
			public KickSelectedUsersAction()
			{
				putValue(SMALL_ICON, new ImageIcon(ClientFrame2.class.getResource("/icons/remove_user-16.png")));
				putValue(LARGE_ICON_KEY, new ImageIcon(ClientFrame2.class.getResource("/icons/remove_user-32.png")));
				putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.META_MASK));
				putValue(NAME, "Kick Selected Users");
				putValue(SHORT_DESCRIPTION, "Send a request to the server to kick the selected users");
			}
			
			@Override
			public void actionPerformed(ActionEvent evt)
			{
				for(int i = 0; i < selectedUsers.size(); ++i) {
					String currentUser = userListModel.getElementAt(i);
					
					if(!currentUser.equals(nameUser)) 
							outPW.println("Kick " + currentUser);
				}
			}
	}

	/**
	 * Classe gérant la fermeture correcte de la fenêtre. La fermeture correcte
	 * de la fenètre implique de lancer un cleanup
	 */
	protected class FrameWindowListener extends WindowAdapter
	{
		/**
		 * Méthode déclenchée à la fermeture de la fenêtre. Envoie la commande
		 * "bye" au serveur
		 */
		@Override
		public void windowClosing(WindowEvent evt)
		{
			logger.info("FrameWindowListener::windowClosing: sending bye ... ");
			if (quitAction != null)
				quitAction.actionPerformed(null);
		}
	}


	public static class ColorTextRenderer extends JLabel implements ListCellRenderer<String>
	{
		private Color color = null;

		@Override
		public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus)
		{
			color = list.getForeground();
			
			if (value != null && value.length() > 0)
				color = new Color(value.hashCode()).brighter();
			
			setText(value);
			
			if (isSelected) {
				setBackground(color);
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(color);
			}
			
			setEnabled(list.isEnabled());
			setFont(list.getFont());
			setOpaque(true);
			
			return this;
		}
	}
	
	//permet de déclencher le menu contextuel
	public class PopupListener extends MouseAdapter
	{
		public void mousePressed(MouseEvent evt)
		{
			maybeShowPopup(evt);
		}
		public void mouseReleased(MouseEvent evt)
		{
			maybeShowPopup(evt);
		}
		private void maybeShowPopup(MouseEvent evt)
		{
			if (evt.isPopupTrigger())
				popupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
		}
	}

	/**
	 * Exécution de la boucle d'exécution. La boucle d'exécution consiste à lire
	 * une ligne sur le flux d'entrée avec un BufferedReader tant qu'une erreur
	 * d'IO n'intervient pas indiquant que le flux a été coupé. Auquel cas on
	 * quitte la boucle principale et on ferme les flux d'I/O avec #cleanup()
	 */
	@Override
	public void run()
	{
		try {
			inOS = new ObjectInputStream(inPipe);
		} catch (IOException e) {
			logger.severe(Failure.CLIENT_INPUT_STREAM
							+ " unable to get user piped in stream");
			logger.severe(e.getLocalizedMessage());
			System.exit(Failure.CLIENT_INPUT_STREAM.toInteger());
		}

		Message messageIn;

		while (commonRun.booleanValue()) {
			messageIn = null;

			try {
				messageIn = (Message) inOS.readObject();
			} catch (IOException e) {
				logger.warning("ClientFrame2: io error at reading");
				break;
			} catch (ClassNotFoundException e) {
				logger.warning("ClientFrame2: class not found reading");
				break;
			}

			if (messageIn != null) {
				storedMessage.add(messageIn);

				if (messageIn.hasAuthor() && !userListModel.contains(messageIn.getAuthor()))
					userListModel.add(messageIn.getAuthor());
			} else {
				break;
			}
			
			try {
				document.remove(0, document.getLength());
			} catch (BadLocationException e) {
				logger.warning("ClientFrame: bad location");
				logger.warning(e.getLocalizedMessage());
			}
			
			Consumer<Message> messagePrinter = (Message message) -> writerMessage(message);

			if (filterButton.isSelected()) {
				Predicate<Message> selectionFilter = (Message message) ->
				{
					if (message != null && message.hasAuthor() && selectedUsers.contains(userListModel.indexOf(message.getAuthor())))
						return true;
					else
						return false;
				};
				storedMessage.stream().sorted().filter(selectionFilter).forEach(messagePrinter);
			} else {
				storedMessage.stream().sorted().forEach(messagePrinter);
			}
		}
		if (commonRun.booleanValue()) {
			logger.info("ClientFrame2::cleanup: changing run state at the end ... ");
			
			synchronized (commonRun) {
				commonRun = Boolean.FALSE;
			}
		}
		cleanup();
	}
	@Override
	public void cleanup()
	{
		logger.info("ClientFrame2::cleanup: closing input buffered reader ... ");
		
		try {
			inOS.close();
		} catch (IOException e) {
			logger.warning("ClientFrame2::cleanup: failed to close input reader" + e.getLocalizedMessage());
		}
		
		super.cleanup();
	}
}
