
package com.mycompany.triviaapp;
import java.util.List;
import java.util.ArrayList;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;


public class TriviaApp {

    public static class ChatClientUI extends JFrame {
        private PrintWriter writer;
        private BufferedReader reader;
        private String username;
        private JButton readyButton, startButton;
        private JPanel waitingPanel;
        private JLabel waitingLabel, usernameLabel;
        private JLabel questionLabel; 
        private boolean isReady = false;
        private JFrame triviaFrame;  
        private TriviaPanel triviaPanel; 
        String[] questions;
        List<String[]> allAnswers;

        public ChatClientUI(Socket socket, String username) throws IOException {
            this.username = username;
            allAnswers = new ArrayList<>();
            OutputStream output = socket.getOutputStream();
            writer = new PrintWriter(output, true);
            InputStream input = socket.getInputStream();
            reader = new BufferedReader(new InputStreamReader(input));

            readyButton = new JButton("I'm Ready!");
            startButton = new JButton("Start Trivia");
            startButton.setEnabled(false); 

            waitingPanel = new JPanel(new GridLayout(3, 1));
            waitingLabel = new JLabel("Waiting for players...", SwingConstants.CENTER);
            usernameLabel = new JLabel(" "); 
            usernameLabel.setHorizontalAlignment(JLabel.CENTER); 



            waitingPanel.add(waitingLabel);
            waitingPanel.add(usernameLabel); 
            waitingPanel.add(readyButton);
            waitingPanel.add(startButton);

            this.setLayout(new BorderLayout());
            this.add(waitingPanel, BorderLayout.CENTER);
            this.pack();
            this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            this.setVisible(true);

            readyButton.addActionListener(e -> {
                toggleReady();
                if (isReady) {
                    writer.println("ready");
                    startButton.setEnabled(true);

                } else {
                    writer.println("not ready");
                    startButton.setEnabled(false);
                    usernameLabel.setText(" "); 

                }
            });

            startButton.addActionListener(e -> {
                writer.println("start");
                System.out.println("start clicked and command sent to server"); 
            });
            
        new Thread(this::handleServerMessages).start();


        }
        
        private void handleServerMessages() {
            String message;
            try {
                while ((message = reader.readLine()) != null) {
                    if (message.startsWith("Questions:")) {
                        String[] receivedQuestions = message.substring("Questions:".length()).split("\\|");
                        questions = receivedQuestions; 
                    } else if (message.startsWith("Answers:")) {
                        String allAnswersString = message.substring("Answers:".length());
                        String[] groupedAnswers = allAnswersString.split("\\|\\|"); 
                        allAnswers.clear(); 
                        for (String group : groupedAnswers) {
                            String[] answers = group.split("\\|");
                        allAnswers.add(answers); 
                        }
                    }
                    else if (message.startsWith("WaitingRoom:")) {
                        updateWaitingRoomDisplay(message.substring("WaitingRoom:".length()).trim());
                    } else if (message.equals("Game is starting!")) {
                        SwingUtilities.invokeLater(this::showTriviaUI); 
                    } else if (message.startsWith("TOP_SCORES")){
                        displayTopScores(message.substring("TOP_SCORES:".length()));
                        triviaFrame.setVisible(false);
                        triviaFrame.dispose();
                    }                   
                    else {
                        System.out.println(message); 
                    }
                }
            } catch (IOException e) {
                System.out.println("Error reading from server: " + e.getMessage());
            }
        }
        
        private void displayTopScores(String scores) {
        String formattedScores = "Top Scores:\n" + scores.replace(", ", "\n");
        JOptionPane.showMessageDialog(this, formattedScores, "Top Scores", JOptionPane.INFORMATION_MESSAGE);
    }

        private void updateWaitingRoomDisplay(String waitingRoomList) {
            SwingUtilities.invokeLater(() -> {
            waitingLabel.setText("Ready Players: " + waitingRoomList); 
            });
        }


        private void toggleReady() {
            isReady = !isReady; 
            readyButton.setBackground(isReady ? Color.GREEN : Color.WHITE);
        }

        private void showTriviaUI() {
            if (triviaFrame == null) {
                triviaFrame = new JFrame("Trivia Question");
                triviaFrame.setSize(400, 300);
                triviaFrame.setLayout(new BorderLayout());
                triviaPanel = new TriviaPanel();
                triviaFrame.add(triviaPanel, BorderLayout.CENTER);
                triviaFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); 
                triviaFrame.setLocationRelativeTo(null); 
            }
            triviaFrame.setVisible(true);
            circulateQuestions();
        }
        
        private void circulateQuestions() {
            Thread questionThread = new Thread(() -> {
                try {
                    for (int i = 0; i < questions.length; i++) { 
                        final int index = i; // To 
                        SwingUtilities.invokeLater(() -> {
                            if (triviaPanel != null) {
                                String[] answers = allAnswers.get(index); 
                                triviaPanel.setQuestion(questions[index], answers); 
                            }
                            triviaFrame.revalidate();
                            triviaFrame.repaint();
                        });
                        Thread.sleep(14000); 
                        SwingUtilities.invokeLater(() -> highlightAnswers(index));
                        Thread.sleep(1000); 
                    }
                sendScoreToServer();  // send score to server

                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); 
                    System.out.println("Question circulation interrupted: " + ex.getMessage());
                }
            });
            questionThread.start();
        }
        
        private void sendScoreToServer() {
            writer.println("SCORE:" + triviaPanel.getScore()); 
        }
        
        
        private void highlightAnswers(int questionIndex) {
            if (triviaPanel != null) {
                String[] answers = allAnswers.get(questionIndex); 
                SwingUtilities.invokeLater(() -> {
                    for (int j = 0; j < triviaPanel.answerButtons.length; j++) {
                        if (triviaPanel.answerButtons[j].getText().equals(answers[4])) { 
                            triviaPanel.answerButtons[j].setBackground(Color.GREEN);
                        } else {
                            triviaPanel.answerButtons[j].setBackground(Color.RED);
                        }
                    }
                });
            }
        }
    }

    public static class TriviaPanel extends JPanel {
        private String question;
        private JButton[] answerButtons = new JButton[4];
        private JPanel buttonPanel;
        private int score = 0; 
        private JLabel questionLabel;
        private String correctAnswer; 
        private JButton selectedButton; 
        private boolean answerSubmitted; 


        public TriviaPanel() {
            setLayout(new BorderLayout());
            
            questionLabel = new JLabel("", SwingConstants.CENTER);
            questionLabel.setFont(new Font("Serif", Font.BOLD, 16));
            add(questionLabel, BorderLayout.NORTH);
            JPanel clockPanel = new JPanel(new BorderLayout());
            add(clockPanel,BorderLayout.CENTER);
            clockTimer clock = new clockTimer();
            clockPanel.add(clock, BorderLayout.CENTER);

            // buttons
            buttonPanel = new JPanel(new GridLayout(2, 2)); 
            for (int i = 0; i < 4; i++) {
                answerButtons[i] = new JButton("Answer " + (i + 1));
                answerButtons[i].addActionListener(this::handleAnswer);
                buttonPanel.add(answerButtons[i]);
            }

            add(buttonPanel, BorderLayout.SOUTH);
        }

        public void setQuestion(String question, String[] answers) {
            this.question = question;
            questionLabel.setText(question);
            correctAnswer = answers[4];
            answerSubmitted = false; 
            selectedButton = null; 



            for (int i = 0; i < answers.length; i++) {
                System.out.println((i + 1) + ": " + answers[i]);
                answerButtons[i].setText(answers[i]);
                answerButtons[i].setBackground(UIManager.getColor("Button.background")); 

            }
        }

        private void handleAnswer(ActionEvent e) {
            JButton clickedButton = (JButton) e.getSource();
            if (!answerSubmitted) { 
                if (clickedButton.getText().equals(correctAnswer)) {
                    score++;
                    System.out.println(score);
                } 
                if (selectedButton == null) { 
                    selectedButton = clickedButton; 
                    selectedButton.setBackground(Color.YELLOW); 
                }
                answerSubmitted = true; 
            }
        }
        
        public int getScore() {
            return score;  
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
        }
    }
    
    
    public static class clockTimer extends JPanel implements Runnable{
        int clockX = 50;
        int clockY = 0;
        int seconds = 0;
        clockTimer(){
            super();
            setOpaque(false);
            Thread clockUpdate = new Thread(this);
            clockUpdate.start();
            
        }
        
        @Override
        public void paintComponent(Graphics g){
            super.paintComponent(g);
            //create clock 
            g.setColor(new Color(0x4F7942));
            g.drawOval(0,0,100,100);
            g.fillOval(0, 0, 100, 100);
            
            g.setColor(Color.BLACK);
            g.drawLine(50, 50, clockX, clockY);
            
            int angle = (int) ((360.0 / 15) * seconds);
            
            g.setColor(Color.RED);
            g.fillArc(0,0,100,100,90,-angle);
            
        }
        
        @Override
        public void run(){
            while(true){
                try{
                    Thread.currentThread().sleep(1000);
                    seconds++;
                    double angle = Math.toRadians(90 - (360.0 / 15) * seconds);
                    if(seconds >= 15){
                        seconds = 0;
                        clockX = 50;
                        clockY = 0;
                    } else{
                        clockX = 50 + (int) (50 * Math.cos(angle)); 
                        clockY = 50 - (int) (50 * Math.sin(angle)); 
                    }
                    
                    SwingUtilities.invokeLater(this::repaint);
                }catch(InterruptedException e){
                    System.out.println("Error");
                    return;
                }
            }
        }
    }


    public static class LoginUI extends JFrame {
        private JTextField textUsername;
        private JTextField textPort;
        private JButton buttonLogin;
        private Socket socket;
        private PrintWriter writer;
        private BufferedReader reader;

        public LoginUI() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            
            JLabel labelUsername = new JLabel("Username:");
            textUsername = new JTextField(20);
            panel.add(labelUsername);
            panel.add(textUsername);


            JLabel labelPort = new JLabel("Port:");
            textPort = new JTextField(20);
            panel.add(labelPort);
            panel.add(textPort);

            buttonLogin = new JButton("Login");
            panel.add(buttonLogin);

            buttonLogin.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        login();
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(null, "Error connecting to server: " + ex.getMessage());
                    }
                }
            });

            add(panel);
            setTitle("Login");
            setSize(300, 200);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            setVisible(true);
        }
        
        private void login() throws IOException {
            String serverAddress = "localhost";
            int port = Integer.parseInt(textPort.getText()); 
            socket = new Socket(serverAddress, port);
            OutputStream output = socket.getOutputStream();
            writer = new PrintWriter(output, true);
            InputStream input = socket.getInputStream();
            reader = new BufferedReader(new InputStreamReader(input));

            String username = textUsername.getText();
            writer.println(username);

            String serverResponse = reader.readLine();
            System.out.println("Server response: " + serverResponse);
                JOptionPane.showMessageDialog(this, "Login successful!");
                this.setVisible(false);
                new ChatClientUI(socket, username); 
            }
        }

    public static void main(String[] args) {

        // This is some framework I found online becuase I was having trouble getting the button color to change and show up
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ex) {}
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new LoginUI();
            }
        });
    }
}
