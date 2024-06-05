package com.example.webchatserver;

import jakarta.ws.rs.core.Link;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * This class represents the data you may need to store about a Chat room
 * You may add more method or attributes as needed
 * **/
public class GameRoom {
    private String  code;
    int[][] board;
    boolean[] turn;
    boolean onGoing = false; //to distinguish between currently ongoing games and open games
    boolean finished = false;//to check if game is finished so no more moves can be made
    int currentPlayerNum = 0; //current player piece, used for DFS

    // maps session ID -> username
    private Map<String, String> users = new HashMap<String, String>() ;
    //keeps track of players in the lobby
    ArrayList<String> players = new ArrayList<>();
    //maps players to int for tracking pieces on board
    Map<String, Integer> playerToNum = new HashMap<>();

    // when created the chat room has at least one user
    public GameRoom(String code, String user){
        this.code = code;
        // when created the user has not entered their username yet
        users.put(user, "");

        board = new int[15][15];
        turn = new boolean[3]; // 0-index buffer, index x being true means it's player x's turn
    }
    public Map<String, String> getUsers() {
        return users;
    }

    /**
     * This method will add the new userID to the room if not exists, or it will add a new userID,name pair
     * **/
    public void setUserName(String userID, String name) {
        // update the name
        if(users.containsKey(userID)){
            users.remove(userID);
            users.put(userID, name);
        }else{ // add new user
            users.put(userID, name);
        }
    }

    /**
     * This method will remove a user from this room
     * **/
    public void removeUser(String userID){
        if(users.containsKey(userID)){
            users.remove(userID);
        }

    }
    /**
     * updates the game given the @row and @col of the move and the @player
     * returns true if someone wins, else false
     * **/
    public boolean updateGame(int row, int col, String player){
        if(!onGoing||finished||!players.contains(player)||!turn[playerToNum.get(player)]){
            // either game hasn't started, or is finished, is a request by a spec, or not their turn
            return false;
        }
        int playerNum = playerToNum.get(player);
        int opp = (playerNum == 1)? 2: 1; //opp must be the other
        board[row][col] = playerNum;
        turn[playerNum] = false;
        turn[opp] = true;

        currentPlayerNum = playerNum;
        return DFS(row, col);//check whether it's a winning move with DFS
    }

    public boolean DFS(int row, int col){//depth first search to check if it's a winning move
        int depth = 1;
        for(depth=1;depth<5;depth++){
            if(!DFSCheck(row-depth,col-depth)){//top left
                break;
            }
            if(depth==4){
                return true;
            }
        }
        for(depth=1;depth<5;depth++){
            if(!DFSCheck(row-depth,col)){//top
                break;
            }
            if(depth==4){
                return true;
            }
        }
        for(depth=1;depth<5;depth++){
            if(!DFSCheck(row-depth,col+depth)){//top right
                break;
            }
            if(depth==4){
                return true;
            }
        }
        for(depth=1;depth<5;depth++){
            if(!DFSCheck(row,col-depth)){//left
                break;
            }
            if(depth==4){
                return true;
            }
        }
        for(depth=1;depth<5;depth++){
            if(!DFSCheck(row,col+depth)){//right
                break;
            }
            if(depth==4){
                return true;
            }
        }
        for(depth=1;depth<5;depth++){
            if(!DFSCheck(row+depth,col-depth)){//bottom left
                break;
            }
            if(depth==4){
                return true;
            }
        }
        for(depth=1;depth<5;depth++){
            if(!DFSCheck(row+depth,col)){//bottom
                break;
            }
            if(depth==4){
                return true;
            }
        }
        for(depth=1;depth<5;depth++){
            if(!DFSCheck(row+depth,col+depth)){//bottom right
                break;
            }
            if(depth==4){
                return true;
            }
        }
        return false;
    }

    public boolean DFSCheck(int r, int c){
        if(r < 0 || r > 14 || c < 0 || c > 14)
            return false;
        if(board[r][c]!=currentPlayerNum){
            return false;
        }
        return true;
    }

    public void gameInit(){
        turn[1] = true;//give first turn to p1
        turn[2] = false;
        onGoing = true;

        playerToNum.put(players.get(0),1);
        playerToNum.put(players.get(1),2);

        finished = false;
        for(int row = 0; row < 15; row ++){
            for (int col=0; col < 15; col ++){
                board[row][col] = 0;
            }
        }
    }

    public String currentTurn(){
        return turn[1] ? players.get(0): players.get(1); //return the username that the current turn is being given to
    }

    public boolean inRoom(String userID){
        return users.containsKey(userID);
    }

//    public String getCode(){
//        return code;
//    }
}
