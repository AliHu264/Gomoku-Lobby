package com.example.webchatserver;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

@WebServlet(name = "refreshServlet", value = "/refreshList")
public class RefreshServlet extends HttpServlet {

    //is called whenever user wants to refresh the room List
    //responds with a json array in plaintext
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE, HEAD");
        PrintWriter out = response.getWriter();

        String roomsJson = "";
        try {
            ChatServer.sem.acquire();
            Iterator<String> itr = ChatServlet.rooms.iterator();
            while(itr.hasNext()){
                String currentRoom = itr.next();

                if(itr.hasNext()){ //checking if the element after currentRoom exists
                    //if there is another element to come
                    roomsJson = roomsJson + "\"" + currentRoom + "\"" + ", ";
                }else{
                    //there are no more users, so don't add a comma to the end
                    roomsJson = roomsJson + "\"" + currentRoom + "\"";
                }
            }
            out.println("{\"rooms\": ["+ roomsJson + "]}");
        } catch (InterruptedException e) {
            ChatServer.sem.release();
            throw new RuntimeException(e);
        }
        ChatServer.sem.release();
    }
    public void destroy() {
    }


}
