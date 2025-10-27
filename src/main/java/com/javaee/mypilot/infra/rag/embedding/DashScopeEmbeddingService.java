package com.javaee.mypilot.infra.rag.embedding;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 阿里云百炼 DashScope Embedding 服务（国内免费）
 * 使用模型：text-embedding-v2
 * API Key 申请：https://dashscope.console.aliyun.com/
 * 免费额度：每月 100 万 tokens
 */
public class DashScopeEmbeddingService implements EmbeddingService {
    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";
    private static final String MODEL = "text-embedding-v2";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String apiKey;
    private final OkHttpClient client;
    private final Gson gson;

    public DashScopeEmbeddingService(String apiKey) {
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
            JsonObject input = new JsonObject();
            JsonArray texts = new JsonArray();
            texts.add(text);
            input.add("texts", texts);

            JsonObject parameters = new JsonObject();
            parameters.addProperty("text_type", "query"); // query 或 document

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", MODEL);
            requestBody.add("input", input);
            requestBody.add("parameters", parameters);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(gson.toJson(requestBody), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("DashScope API 调用失败: " + response.code() + " - " + response.message());
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                // 提取嵌入向量
                JsonObject output = jsonResponse.getAsJsonObject("output");
                JsonArray embeddings = output.getAsJsonArray("embeddings");
                JsonObject firstEmbedding = embeddings.get(0).getAsJsonObject();
                JsonArray embeddingArray = firstEmbedding.getAsJsonArray("embedding");

                float[] embedding = new float[embeddingArray.size()];
                for (int i = 0; i < embeddingArray.size(); i++) {
                    embedding[i] = embeddingArray.get(i).getAsFloat();
                }

                return embedding;
            }
        } catch (IOException e) {
            throw new RuntimeException("生成 DashScope 嵌入向量失败: " + e.getMessage(), e);
        }
    }
}
