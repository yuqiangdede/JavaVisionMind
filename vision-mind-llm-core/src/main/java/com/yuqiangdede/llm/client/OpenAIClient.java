package com.yuqiangdede.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Slf4j
public class OpenAIClient {


    /**
     * 调用 OpenAI Chat Completions 接口并返回纯文本回答。
     *
     * @param openaiBaseUrl OpenAI 接口地址
     * @param openaiKey     OpenAI API Key
     * @param openaiModel   目标模型名称
     * @param prompt        用户输入的提示词
     */
    public static String chat(String openaiBaseUrl, String openaiKey, String openaiModel, String prompt) throws IOException {
        String responseJson = sendPrompt(openaiBaseUrl, openaiKey, openaiModel, prompt);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(responseJson);
        String content = root.get("choices").get(0).get("message").get("content").asText();
        return content.trim();
    }

    public static String sendPrompt(String openaiBaseUrl, String openaiKey, String openaiModel, String prompt) throws IOException {
        URL url = new URL(openaiBaseUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + openaiKey);
        conn.setDoOutput(true);

        // 构建请求体：仅传入单轮对话的 user 内容，避免提示词中的引号影响 JSON
        String body = String.format(
                "{\n" +
                        "  \"model\": \"%s\",\n" +
                        "  \"messages\": [\n" +
                        "    {\"role\": \"user\", \"content\": \"%s\"}\n" +
                        "  ]\n" +
                        "}", openaiModel, prompt.replace("\"", "\\\"")
        );

        log.debug("OpenAIClient POST to: {}", openaiBaseUrl);
        log.debug("OpenAIClient Body: {}", body);

        // 发送请求体
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        // 读取返回：接口以 JSON 字符串返回，逐行拼接避免遗漏换行符号
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                log.debug("OpenAIClient Response: {}", line);
                response.append(line.trim());
            }
            return response.toString();
        }
    }


}
