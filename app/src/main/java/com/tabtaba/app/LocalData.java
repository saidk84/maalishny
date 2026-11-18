package com.tabtaba.app;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class LocalData {
    static final int LIMIT = 16 * 1024 * 1024;
    private final AtomicFile file;
    JSONObject data;

    LocalData(Context context) throws Exception {
        file = new AtomicFile(new File(context.getFilesDir(), "tabtaba.json"));
        try (InputStream input = file.openRead()) {
            data = parse(read(input));
        } catch (FileNotFoundException missing) {
            if (file.getBaseFile().exists()) throw missing;
            data = new JSONObject();
            data.put("version", 1);
            data.put("online", true);
            data.put("history", new JSONArray());
        }
    }

    static byte[] read(InputStream input) throws IOException {
        if (input == null) throw new IOException("File unavailable");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > LIMIT + 64) throw new IOException("File too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    static JSONObject parse(byte[] bytes) throws Exception {
        if (bytes.length > LIMIT) throw new IOException("Data too large");
        JSONObject data = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (data.getInt("version") != 1) throw new IOException("Unsupported version");
        data.getBoolean("online");
        JSONArray history = data.getJSONArray("history");
        for (int i = 0; i < history.length(); i++) {
            JSONObject entry = history.getJSONObject(i);
            entry.getString("kind");
            entry.getString("text");
            entry.getString("person");
            entry.getLong("time");
        }
        return data;
    }

    void save(JSONObject next) throws Exception {
        byte[] bytes = next.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > LIMIT) throw new IOException("History storage limit reached");
        FileOutputStream output = file.startWrite();
        try {
            output.write(bytes);
            file.finishWrite(output);
        } catch (IOException failure) {
            file.failWrite(output);
            throw failure;
        }
        data = next;
    }

    JSONObject copy() throws Exception {
        return new JSONObject(data.toString());
    }
}
