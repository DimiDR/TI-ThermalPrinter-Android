package com.dantsu.thermalprinter.helpClasses;

import android.content.Context;
import android.widget.Toast;

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
                Integer shop_id = userObject.getInt("shop_id");
                String username = userObject.getString("username");
                String password = userObject.getString("password");
                String shop_name = userObject.getString("shop_name");
                String domain_shop = userObject.getString("domain_shop");
                String domain_website = userObject.getString("domain_website");
                String kitchen_view = userObject.getString("kitchen_view");
                Integer location_id = userObject.getInt("location_id");
                users.add(new User(shop_id, username, password, shop_name, domain_shop,
                        domain_website, kitchen_view, location_id));
            }

        } catch (IOException | JSONException e) {
            e.printStackTrace();
            Toast.makeText(context, "Check if users.json exists", Toast.LENGTH_SHORT).show();
        }

        return users;
    }

    public static class User {
        public String username;
        public String password;
        public String shop_name;
        public String domain_shop;
        public String domain_website;
        public String kitchen_view;
        public Integer shop_id;
        public Integer location_id;

        public User(Integer shop_id, String username, String password, String shop_name,
                    String domain_shop, String domain_website, String kitchen_view, Integer location_id) {
            this.shop_id = shop_id;
            this.username = username;
            this.password = password;
            this.shop_name = shop_name;
            this.domain_shop = domain_shop;
            this.domain_website = domain_website;
            this.kitchen_view = kitchen_view;
            this.location_id = location_id;

        }
    }
}
