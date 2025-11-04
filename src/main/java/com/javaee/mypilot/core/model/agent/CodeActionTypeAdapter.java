package com.javaee.mypilot.core.model.agent;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.javaee.mypilot.core.enums.CodeOpt;

import java.io.IOException;

/**
 * CodeAction 的自定义 Gson TypeAdapter
 * 用于处理 JSON 中的 "type" 字段到 CodeOpt 枚举的转换
 */
public class CodeActionTypeAdapter extends TypeAdapter<CodeAction> {

    @Override
    public void write(JsonWriter out, CodeAction value) throws IOException {
        out.beginObject();
        out.name("type").value(value.getOpt().name()); // Serialize enum as its name string
        out.name("filePath").value(value.getFilePath());
        out.name("startLine").value(value.getStartLine());
        out.name("endLine").value(value.getEndLine());
        out.name("oldCode").value(value.getOldCode());
        out.name("newCode").value(value.getNewCode());
        out.endObject();
    }

    @Override
    public CodeAction read(JsonReader in) throws IOException {
        CodeAction action = new CodeAction();
        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            switch (name) {
                case "type":
                    String typeString = in.nextString();
                    try {
                        action.setOpt(CodeOpt.valueOf(typeString.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        System.err.println("Unknown CodeOpt type: " + typeString);
                        action.setOpt(null); // Or handle as an error
                    }
                    break;
                case "filePath":
                    action.setFilePath(in.nextString());
                    break;
                case "startLine":
                    action.setStartLine(in.nextInt());
                    break;
                case "endLine":
                    action.setEndLine(in.nextInt());
                    break;
                case "oldCode":
                    action.setOldCode(in.nextString());
                    break;
                case "newCode":
                    action.setNewCode(in.nextString());
                    break;
                default:
                    in.skipValue(); // Ignore unknown fields
                    break;
            }
        }
        in.endObject();
        return action;
    }
}

