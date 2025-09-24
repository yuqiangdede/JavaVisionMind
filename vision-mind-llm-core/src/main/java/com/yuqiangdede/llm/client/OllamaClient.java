package com.yuqiangdede.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class OllamaClient {


    public static String chat(String ollamaBaseUrl, String ollamaModel, String prompt) throws IOException {
        String s = sendPrompt(ollamaBaseUrl, ollamaModel, prompt);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(s);
        String response = root.get("response").asText();
        return response.trim();
    }

    public static String sendPrompt(String ollamaBaseUrl, String ollamaModel, String prompt) throws IOException {
        URL url = new URL(ollamaBaseUrl + "/api/generate");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // 构建请求体
        String body = String.format(
                "{\"model\":\"%s\",\"prompt\":\"%s\",\"stream\":false}",
                ollamaModel, prompt.replace("\"", "\\\"")
        );

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        // 读取返回
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
            return response.toString();
        }
    }


}
