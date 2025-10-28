package com.javaee.mypilot.infra.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.javaee.mypilot.infra.AppExecutors;
import com.javaee.mypilot.service.ConfigService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 大语言模型客户端，负责与大语言模型进行交互，发送请求并接收响应。
 * 支持调用 DeepSeek、OpenAI 兼容的 API
 */
@Service(Service.Level.PROJECT)
public final class LlmClient {

    private final Project project;
    private final ConfigService configService;
    private final Gson gson;
    private final AppExecutors appExecutors;

    public LlmClient(Project project) {
        this.project = project;
        this.configService = ConfigService.getInstance(project);
        this.gson = new Gson();
        this.appExecutors = AppExecutors.getInstance(project);
    }

    /**
     * 异步调用 LLM API 生成回答
     * @param prompt 完整的 prompt
     * @return 包含生成的回答文本的异步任务
     * @throws Exception 如果调用失败
     */
    public CompletableFuture<String> chatAsync(String prompt) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return chat(prompt);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, appExecutors.getIoExecutor());
    }

    /**
     * 调用 LLM API 生成回答
     * @param prompt 完整的 prompt
     * @return 生成的回答文本
     * @throws Exception 如果调用失败
     */
    public String chat(String prompt) throws Exception {
        String apiEndpoint = configService.getLlmApiEndpoint();
        String apiKey = configService.getLlmApiKey();
        String model = configService.getLlmModel();

        // 构建请求体
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);

        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.7);
        requestBody.addProperty("max_tokens", 2000);

        String requestBodyStr = gson.toJson(requestBody);

        // 发送 HTTP 请求
        URL url = new URL(apiEndpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            // 设置请求头
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000); // 30秒连接超时
            conn.setReadTimeout(60000); // 60秒读取超时

            // 发送请求体
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBodyStr.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 读取响应
            int responseCode = conn.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 成功响应
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    // 解析响应
                    return parseResponse(response.toString());
                }
            } else {
                // 错误响应
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        errorResponse.append(responseLine.trim());
                    }
                    throw new Exception("LLM API 调用失败 (HTTP " + responseCode + "): " + errorResponse);
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 解析 API 响应，提取生成的文本
     */
    private String parseResponse(String jsonResponse) throws Exception {
        try {
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);

            // 标准 OpenAI 格式: choices[0].message.content
            if (response.has("choices")) {
                JsonArray choices = response.getAsJsonArray("choices");
                if (!choices.isEmpty()) {
                    JsonObject firstChoice = choices.get(0).getAsJsonObject();
                    if (firstChoice.has("message")) {
                        JsonObject message = firstChoice.getAsJsonObject("message");
                        if (message.has("content")) {
                            return message.get("content").getAsString();
                        }
                    }
                }
            }

            throw new Exception("无法从 API 响应中提取生成的文本: " + jsonResponse);
        } catch (Exception e) {
            throw new Exception("解析 API 响应失败: " + e.getMessage());
        }
    }
}

