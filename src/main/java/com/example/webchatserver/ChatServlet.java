package com.example.webchatserver;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import jakarta.websocket.server.PathParam;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * This is a class that has services
 * In our case, we are using this to generate unique room IDs**/
@WebServlet(name = "chatServlet", value = "/chat-servlet")
public class ChatServlet extends HttpServlet {
    private String message;

    //static so this set is unique
    public static Set<String> rooms = new HashSet<>();

    /**
     * Method generates unique room codes
     * **/
    public String generatingRandomUpperAlphanumericString(int length) throws InterruptedException {
        ChatServer.sem.acquire();
        String generatedString = RandomStringUtils.randomAlphanumeric(length).toUpperCase();
        // generating unique room code
        while (rooms.contains(generatedString)){
            generatedString = RandomStringUtils.randomAlphanumeric(length).toUpperCase();
        }
        rooms.add(generatedString);

        ChatServer.sem.release();
        return generatedString;
    }

    //is called whenever user wants to create a new room
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/plain");
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE, HEAD");

        // send the random code as the response's content
        PrintWriter out = response.getWriter();
        try {
            out.println(generatingRandomUpperAlphanumericString(5));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    public void destroy() {
    }
}