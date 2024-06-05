package com.example.webchatserver;


import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * This class represents a web socket server, a new connection is created and it receives a roomID as a parameter
 * **/
@ServerEndpoint(value="/ws/{roomID}")
public class ChatServer {

    // maps roomID -> chatRoom object for ease of access
    static HashMap<String, GameRoom> roomMap = new HashMap<>();
    //semaphore to allow access to the game rooms without race condition
    public static Semaphore sem = new Semaphore(1,true);


    @OnOpen
    public void open(@PathParam("roomID") String roomID, Session session) throws IOException, EncodeException, InterruptedException {
        sem.acquire();
        //check if this is a new room
        if(!roomMap.containsKey(roomID)){
            GameRoom newRoom = new GameRoom(roomID, session.getId());
            roomMap.put(roomID,newRoom);
        }

        //message to tell client to update the "current-room" user is in
        session.getBasicRemote().sendText("{\"type\": \"roomUpdate\", \"message\":\"You are currently in room "+roomID+"\"}");


        //welcome message
        session.getBasicRemote().sendText("{\"type\": \"chat\", \"username\": \"server\", \"message\":\"Welcome to room "+roomID+"." +
                " Please state your username to begin.\"}");

        roomMap.get(roomID).setUserName(session.getId(), "");//add the user to the room as default username
                                                                    // function also checks for duplicates and handles it correctly
        sem.release();
    }

    @OnClose
    public void close(Session session) throws IOException, EncodeException, InterruptedException {
        sem.acquire();
        String userId = session.getId();
        String username = "";
        GameRoom currentRoom = null;
        String usernameListString = "";

        for(Map.Entry<String, GameRoom> entry: roomMap.entrySet()){//go through every chatroom
            if(entry.getValue().inRoom(userId)){//if the user is in the chat room
                currentRoom = entry.getValue(); //only a shallow copy but is fine for what we need
                username = currentRoom.getUsers().get(userId);//record the username of the session that closed
                currentRoom.players.remove(currentRoom.getUsers().get(userId));//if it's also a player, remove them
                currentRoom.removeUser(userId);//remove them

                if(currentRoom.onGoing && currentRoom.players.size()==1){//if they leave in the middle of the game
                    currentRoom.finished = true;//end immediately and give the other player the win
                    for (Session peer : session.getOpenSessions()){
                        if(currentRoom.inRoom(peer.getId())){
                            peer.getBasicRemote().sendText("{\"type\": \"game-end\", \"winner\": \"" + currentRoom.players.get(0) + "\"}");
                        }
                    }
                }

                String roomID = entry.getKey();
                if(currentRoom.getUsers().isEmpty()){//remove the room if no users are in it once someone leaves
                    roomMap.remove(roomID);
                    ChatServlet.rooms.remove(roomID); //remove from the servlet as well
                }
                break;
            }
        }
        sem.release();

        if(currentRoom == null){
            throw new RuntimeException("Room does not exist");
        }
        //send leave message to all other members in the room
        for (Session peer : session.getOpenSessions()){
            if(currentRoom.inRoom(peer.getId())){//send only to sessions in the chatroom
                peer.getBasicRemote().sendText("{\"type\": \"chat\", \"username\": \"server\", \"message\":\"" + username
                        + " left the chat room.\"}");
                usernameListString += "\""+ currentRoom.getUsers().get(peer.getId())+ "\"" + ", ";
            }
        }
        usernameListString = usernameListString.substring(0,usernameListString.length()-2); //remove the last space and comma
        //send the updated all the clients again
        for (Session peer : session.getOpenSessions()){
            if(currentRoom.inRoom(peer.getId())){
                peer.getBasicRemote().sendText("{\"type\": \"status\", \"message\": [" + usernameListString +"]}");
            }
        }


        if(currentRoom.getUsers().size() >= 2){ //if there are two or more users still in the room, make one of them the new player
            String newPlayerName = "";

            //loop though peers and make first non-player peer a player
            for(Session peer: session.getOpenSessions()){
                newPlayerName = currentRoom.getUsers().get(peer.getId());
                if(currentRoom.inRoom(peer.getId()) && !currentRoom.players.contains(newPlayerName)){ //if current peer is in the room (as a user) and they are not a player...
                    currentRoom.players.add(newPlayerName); //make the current peer a player
                    break;
                }
            }


            for (Session peer : session.getOpenSessions()){
                //send all users that new player is now a player
                if(currentRoom.inRoom(peer.getId())){//send only to sessions in the chatroom
                    peer.getBasicRemote().sendText("{\"type\": \"chat\", \"username\": \"server\", \"message\":\"" + newPlayerName + " is now a player!\"}");
                }

                //tell clients they can start a new game
                if(currentRoom.players.contains(currentRoom.getUsers().get(peer.getId()))){//send only to player clients (non-spectators)
                    peer.getBasicRemote().sendText("{\"type\": \"gameInit\", \"message\": \"True\"}");//tell client that game is ready
                }
            }
        }

    }

    @OnMessage
    public void handleMessage(String comm, Session session) throws IOException, EncodeException, InterruptedException {
        String userId = session.getId();
        //String roomID = roomMap.get(userId).getCode();
        String username = "";
        JSONObject jsonmsg = new JSONObject(comm);
        String message = (String) jsonmsg.get("msg");
        GameRoom currentRoom = null;

        String messageType = ((String) jsonmsg.get("type"));

        sem.acquire();
        //find currentRoom
        for(Map.Entry<String, GameRoom> entry: roomMap.entrySet()){//get current room of user and their username
            if(entry.getValue().inRoom(userId)){
                currentRoom = entry.getValue();
                username = currentRoom.getUsers().get(userId);
                break;
            }
        }
        sem.release();

        //if json message indicates that a user is typing
        if(messageType.equals("typing")){
            //send the typing indicator to all other users in the same chat room
            for(Session peer: session.getOpenSessions()){
                if(currentRoom.inRoom(peer.getId()) && !Objects.equals(peer.getId(), userId)){
                    //if the user hasn't created their username yet, indicate this instead
                    if(username.isEmpty()){
                        peer.getBasicRemote().sendText("{\"type\": \"typing\", \"message\":\" a new user is typing their username...\"}");
                    }
                    else{
                        peer.getBasicRemote().sendText("{\"type\": \"typing\", \"message\":\"" + username + " is typing...\"}");
                    }
                }
            }
        }
        // else if json message indicates that a user updated their status
        else if(messageType.equals("user-status") && !username.isEmpty()){
            //for every person in the same chat room
            for(Session peer: session.getOpenSessions()){
                if(currentRoom.inRoom(peer.getId())){
                    //update the status message
                    peer.getBasicRemote().sendText("{\"type\": \"user-status\", \"username\": \"" + username + "\", \"message\":\"" + message +"\"}");
                }
            }
        }
        //else if json message indicates that a user sent an image
        else if(messageType.equals("image") && !username.isEmpty()){
            //for every person in the same chat room
            for(Session peer: session.getOpenSessions()){
                if(currentRoom.inRoom(peer.getId())){
                    peer.getBasicRemote().sendText("{\"type\": \"image\", \"username\": \"" + username + "\", \"message\":\"" + message +"\"}");
                }
            }
        }
        //if json message indicates game initialization
        else if(messageType.equals("gameInit") && !username.isEmpty()){ //expects "msg": "True"
            sem.acquire();
            if(currentRoom.players.size()<2){//check if one of the users left before game started
                for(Session peer: session.getOpenSessions()){
                    if(currentRoom.inRoom(peer.getId())){//update all users on what the current player's turn is, currentTurn() returns a username
                        peer.getBasicRemote().sendText("{\"type\": \"chat\", \"username\": \"" + "server" + "\", \"message\":\"" + "Game does not have 2 Players" +"\"}");
                    }
                }
                sem.release();
                return;
            }
            currentRoom.gameInit();
            for(Session peer: session.getOpenSessions()){
                if(currentRoom.inRoom(peer.getId())){//update all users on what the current player's turn is, currentTurn() returns a username
                    //send message to clear board
                    peer.getBasicRemote().sendText("{\"type\": \"clear-board\"}");
                    peer.getBasicRemote().sendText("{\"type\": \"game-turn\", \"currentTurn\": \"" + currentRoom.currentTurn() + "\"}");
                    peer.getBasicRemote().sendText("{\"type\": \"game-pieces\", \"black\": \"" + currentRoom.players.get(0) + "\", \"white\": \"" + currentRoom.players.get(1) + "\"}");
                    peer.getBasicRemote().sendText("{\"type\": \"chat\", \"username\": \"" + "server" + "\", \"message\":\"" + "Game Started!" +"\"}");
                }
            }
            sem.release();
        }
        else if(messageType.equals("updateGame") && !username.isEmpty()){// expects a comma seperated string "msg": "<row>,<col>"
            sem.acquire();
            String[] move = message.split(",");
            int r = Integer.parseInt(move[0]);
            int c = Integer.parseInt(move[1]);

            if(currentRoom.finished == true){
                sem.release();
                return;
            }

            if(currentRoom.updateGame(r,c,username)){//if this returns true, it means it's a winning move
                currentRoom.finished = true;
                for(Session peer: session.getOpenSessions()){
                    if(currentRoom.inRoom(peer.getId())){
                        peer.getBasicRemote().sendText("{\"type\": \"game-status\", \"move\": \"" + r + "," + c + "\", \"player\": \""+ username +"\"}");
                        peer.getBasicRemote().sendText("{\"type\": \"game-end\", \"winner\": \"" + username + "\"}");

                        //if a player has won, let client know they can start a new game
                        if(currentRoom.players.contains(currentRoom.getUsers().get(peer.getId()))){//send only to player clients (non-spectators)
                            peer.getBasicRemote().sendText("{\"type\": \"gameInit\", \"message\": \"True\"}");//tell client that game is ready
                        }

                    }
                }
                sem.release();
                return;
            }

            //always return game state
            for(Session peer: session.getOpenSessions()){
                if(currentRoom.inRoom(peer.getId())){
                    peer.getBasicRemote().sendText("{\"type\": \"game-turn\", \"currentTurn\": \"" + currentRoom.currentTurn() + "\"}");
                    peer.getBasicRemote().sendText("{\"type\": \"game-status\", \"move\": \"" + r + "," + c + "\", \"player\": \""+ username +"\"}");
                }
            }

            sem.release();
        }
        //else (if the user sent a normal text message)
        else if(messageType.equals("chat")){
            //not their first message, so their username isn't "" (not empty)
            if(!username.isEmpty()){
                //send the message as json objects
                for(Session peer: session.getOpenSessions()){
                    if(currentRoom.inRoom(peer.getId())){
                        peer.getBasicRemote().sendText("{\"type\": \"chat\", \"username\": \"" + username + "\", \"message\":\"" + message +"\"}");
                    }
                }
            }
            else{//this is their first message
                //find the chatroom and then set username
                username = message;
                boolean gameReady = false;

                sem.acquire();//acquire permit for accessing game rooms first

                for(Map.Entry<String, GameRoom> entry: roomMap.entrySet()){//update the game room of the user
                    GameRoom currRoom = entry.getValue();
                    if(currRoom.inRoom(userId)){
                        currRoom.setUserName(userId,username);//set their username in the chatroom
                        if(currRoom.players.size()<2){//if there are less than 2 players
                            currRoom.players.add(username);//add to players
                            if(currRoom.players.size() == 2){
                                gameReady = true;
                            }
                        }
                    }
                }

                //make the user list string from all current usernames in the chat room
                Collection<String> userList = currentRoom.getUsers().values();
                String userListString = "";
                for(String s: userList){
                    userListString += "\""+s+"\""+", ";
                }


                userListString = userListString.substring(0,userListString.length()-2);//remove the last space and comma
                for(Session peer: session.getOpenSessions()){
                    if(currentRoom.inRoom(peer.getId())){
                        peer.getBasicRemote().sendText("{\"type\": \"chat\", \"username\": \"server\", \"message\":\"" + username + " joined the chat room.\"}");//join message
                        peer.getBasicRemote().sendText("{\"type\": \"status\", \"message\": [" + userListString +"]}");//tell client to update user lists

                        String currUsername = currentRoom.getUsers().get(peer.getId());
                        if(currentRoom.players.contains(currUsername)&&gameReady){//send only to player clients (non-spectators)
                            peer.getBasicRemote().sendText("{\"type\": \"gameInit\", \"message\": \"True\"}");//tell client that game is ready
                        }
                    }
                }
                sem.release();
            }
        }
    }


}
