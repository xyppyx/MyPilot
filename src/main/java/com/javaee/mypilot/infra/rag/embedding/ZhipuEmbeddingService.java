package com.javaee.mypilot.infra.rag.embedding;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 智谱 AI Embedding 服务（国内免费）
 * 使用模型：embedding-2
 * API Key 申请：https://open.bigmodel.cn/
 * 免费额度：每月一定量的免费调用
 */
public class ZhipuEmbeddingService implements EmbeddingService {
    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/embeddings";
    private static final String MODEL = "embedding-2";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String apiKey;
    private final OkHttpClient client;
    private final Gson gson;

    public ZhipuEmbeddingService(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    @Override
    public float[] embed(String text) {
        try {
            // 构建请求体
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", MODEL);
            requestBody.addProperty("input", text);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(gson.toJson(requestBody), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("智谱 AI API 调用失败: " + response.code() + " - " + response.message());
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                // 提取嵌入向量
                JsonArray data = jsonResponse.getAsJsonArray("data");
                JsonObject firstResult = data.get(0).getAsJsonObject();
                JsonArray embeddingArray = firstResult.getAsJsonArray("embedding");

                float[] embedding = new float[embeddingArray.size()];
                for (int i = 0; i < embeddingArray.size(); i++) {
                    embedding[i] = embeddingArray.get(i).getAsFloat();
                }

                return embedding;
            }
        } catch (IOException e) {
            throw new RuntimeException("生成智谱 AI 嵌入向量失败: " + e.getMessage(), e);
        }
    }
}
