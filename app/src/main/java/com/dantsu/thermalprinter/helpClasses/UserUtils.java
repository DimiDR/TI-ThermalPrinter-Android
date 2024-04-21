package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class UserUtils {

    public static List<User> getUsers(Context context) {
        List<User> users = new ArrayList<>();

        try {
            InputStream is = context.getAssets().open("users.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();

            String json = new String(buffer, "UTF-8");
            JSONObject jsonObject = new JSONObject(json);
            JSONArray jsonArray = jsonObject.getJSONArray("users");

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject userObject = jsonArray.getJSONObject(i);
                String username = userObject.getString("username");
                String password = userObject.getString("password");
                String domain_shop = userObject.getString("domain_shop");
                String domain_website = userObject.getString("domain_website");
                users.add(new User(username, password, domain_shop, domain_website));
            }

        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        return users;
    }

    public static class User {
        public String username;
        public String password;
        public String domain_shop;
        public String domain_website;

        public User(String username, String password, String domain_shop, String domain_website) {
            this.username = username;
            this.password = password;
            this.domain_shop = domain_shop;
            this.domain_website = domain_website;
        }
    }
}
