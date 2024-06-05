package com.example.webchatserver;

import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

public class ChatLogResource {
    public String getRoomHistory(String roomID) {
        URL url = this.getClass().getClassLoader().getResource("/chatHistory");
        String history = "";
        File mainDir = null;

        try {
            mainDir = new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }


        try {
            history = FileReaderWriter.readHistoryFile(mainDir, roomID + ".json");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }


        JSONObject mapper = new JSONObject();
        mapper.put("room", roomID);
        if(history!=null){
            mapper.put("log", history);
        }else{
            mapper.put("log", "");
        }

        return mapper.toString();
    }


    public void saveRoomHistory( String roomID, String content) {

        // parse the consumed json data
        System.out.println(content);
        JSONObject mapper = new JSONObject(content);
        Map<String,Object> result = mapper.toMap();
        String filename = (String) result.get("room");

        URL url = this.getClass().getClassLoader().getResource("/chatHistory");

        File data = null;
        try {
            System.out.println(url.toURI());
            data = new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        try {
            // save the chat log history to the roomID.json file in the resources folder
            FileReaderWriter.saveNewFile(data, filename+".json", (String) result.get("log"));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}