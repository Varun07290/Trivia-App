package com.mycompany.triviaserver;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TriviaServer {
    private static final int PORT = 5190;
    private static List<ClientHandler> clients = new ArrayList<>();
    private static List<String> triviaQuestions = new ArrayList<>();
    private static List<List<String>> answerChoices = new ArrayList<>();
    private static boolean gameStarted = false;
    private static Connection connection;
    private static Map<String, Integer> scoresMap = new HashMap<>();



    public static void main(String[] args) throws IOException {
        connectToDatabase();
        ServerSocket serverSocket = new ServerSocket(PORT);
        loadTriviaQuestions();

        try {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler clientThread = new ClientHandler(socket);
                clients.add(clientThread); 
                clientThread.start();
            }
        } finally {
            serverSocket.close();
        }
    }
    
   
    
    private static void connectToDatabase() {
        String url = "jdbc:mysql://localhost:8889/trivia_app";
        String username = "root";
        String password = "root";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(url, username, password);
            System.out.println("Connected to the database successfully.");
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("Database connection failed: " + e.getMessage());
        } 
    }
        
    
    
    public static synchronized void updateScores(String username, int score) {
        scoresMap.put(username, score);
        System.out.println("Updated Scores: " + scoresMap); 
        broadcastTopThreeScores(); 

    }
    
    private static List<Map.Entry<String, Integer>> getTopThreeScores() {
        List<Map.Entry<String, Integer>> sortedScores = new ArrayList<>(scoresMap.entrySet());
        sortedScores.sort((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()));
        return sortedScores.subList(0, Math.min(3, sortedScores.size()));
    }
    
    
    private static void broadcastTopThreeScores() {
        List<Map.Entry<String, Integer>> topThree = getTopThreeScores();

        StringBuilder scoreMessage = new StringBuilder("TOP_SCORES:");
        for (Map.Entry<String, Integer> entry : topThree) {
            scoreMessage.append(entry.getKey()).append(" - ").append(entry.getValue()).append(", ");
        }

        if (scoreMessage.length() > "TOP_SCORES:".length()) {
            scoreMessage.setLength(scoreMessage.length() - 2);
        }

        for (ClientHandler client : clients) {
            client.sendMessage(scoreMessage.toString());
        }
    }
    

    private static void loadTriviaQuestions() {
        String query = "SELECT question_text, answer1, answer2, answer3, answer4, correct_answer FROM Questions";
         try (PreparedStatement stmt = connection.prepareStatement(query)) {
             ResultSet rs = stmt.executeQuery();
             triviaQuestions.clear();  
             answerChoices.clear();    
             while (rs.next()) {
                 triviaQuestions.add(rs.getString("question_text"));  
                 List<String> answers = new ArrayList<>();
                 answers.add(rs.getString("answer1"));
                 answers.add(rs.getString("answer2"));
                 answers.add(rs.getString("answer3"));
                 answers.add(rs.getString("answer4"));
                 answers.add(rs.getString("correct_answer"));  
                 answerChoices.add(answers);  
             }
         } catch (SQLException e) {
             System.out.println("Error loading trivia questions and answers: " + e.getMessage());
         }
    }

    private static synchronized void addToWaitingRoom(ClientHandler client) {
        if (!clients.contains(client)) {
            clients.add(client);
            System.out.println(client.getUserName() + " added to the waiting room");
            updateWaitingRoom();  
        }
    }

    private static synchronized void removeFromWaitingRoom(ClientHandler client) {
        if (clients.remove(client)) {
            System.out.println(client.getUserName() + " removed from the waiting room");
            updateWaitingRoom();  
        }
    }

    private static synchronized void updateWaitingRoom() {
        StringBuilder sb = new StringBuilder("WaitingRoom:");
        for (ClientHandler client : clients) {
            if (client.isReady()){
                sb.append(client.getUserName()).append(", ");
            }
        }
        String waitingRoomList = sb.toString().replaceAll(", $", "");
        System.out.println("Current clients in the waiting room: " + waitingRoomList); 
        for (ClientHandler client : clients) {
            client.sendMessage(waitingRoomList);
        }
    }


    private static synchronized void startGame() {
        if (!gameStarted && !clients.isEmpty()) {
            gameStarted = true;
            String questions = String.join("|", triviaQuestions);  
            StringBuilder allAnswers = new StringBuilder();

            for (List<String> answers : answerChoices) {
                allAnswers.append(String.join("|", answers)).append("||");
            }

            if (allAnswers.length() > 0) {
                allAnswers.setLength(allAnswers.length() - 2);
            }

            for (ClientHandler client : clients) {
                if (client.isReady()) {
                    client.sendMessage("Game is starting!");
                    client.sendMessage("Questions:" + questions);
                    client.sendMessage("Answers:" + allAnswers.toString());
                }
            }
        }
    }
    
    

    static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter writer;
        private BufferedReader reader;
        private String userName;
        private AtomicBoolean authenticated = new AtomicBoolean(false);
        private boolean isReady = false;  


        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        void setReady(boolean ready) {
            this.isReady = ready;
            TriviaServer.updateWaitingRoom();  
           }

        boolean isReady() {
            return isReady;
        }

        public void run() {
            try {
                InputStream input = socket.getInputStream();
                reader = new BufferedReader(new InputStreamReader(input));
                OutputStream output = socket.getOutputStream();
                writer = new PrintWriter(output, true);

                authenticateUser(reader.readLine());

                String clientMessage;
                while ((clientMessage = reader.readLine()) != null) {
                    if ("bye".equalsIgnoreCase(clientMessage.trim())) {
                        removeFromWaitingRoom(this);
                        clients.remove(this);
                        break; 
                    } else if ("ready".equalsIgnoreCase(clientMessage.trim())) {
                        setReady(true);
                        addToWaitingRoom(this);
                    } 
                    else if ("not ready".equalsIgnoreCase(clientMessage.trim())) {
                        setReady(false);
                        removeFromWaitingRoom(this);
                    }
                    else if ("start".equalsIgnoreCase(clientMessage.trim())) {
                        startGame();
                    }
                    else if (clientMessage.startsWith("SCORE:")) {
                        handleScore(clientMessage);
                    }
                }
            } catch (IOException ex) {
                System.out.println("Error: " + ex.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException ex) {}
            }
        }
        
        void handleScore(String scoreMessage) {
            int score = Integer.parseInt(scoreMessage.split(":")[1].trim());
            TriviaServer.updateScores(getUserName(), score);
        }

        void authenticateUser(String userInfo) {
            userName = userInfo.split(":")[0];
            authenticated.set(true);
            writer.println("200");
        }

        void sendMessage(String message) {
            writer.println(message);
        }

        String getUserName() {
            return userName;
        }
    }
}
