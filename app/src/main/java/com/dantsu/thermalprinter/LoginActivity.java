package com.dantsu.thermalprinter;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class LoginActivity extends AppCompatActivity {
        private EditText etUsername, etPassword;
        private Button btnLogin;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_login);

            etUsername = findViewById(R.id.etUsername);
            etPassword = findViewById(R.id.etPassword);
            btnLogin = findViewById(R.id.btnLogin);

            btnLogin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String username = etUsername.getText().toString();
                    String password = etPassword.getText().toString();

                    if (isValidCredentials(username, password)) {

                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, "Invalid credentials", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        private boolean isValidCredentials(String username, String password) {
            // Hardcoded credentials validation
            //TODO check user and PW from users.json
            //TODO: use UserUtils.java
            return "admin".equals(username) && "password123".equals(password);
        }

        private void saveUserUrl(String username) {
            // Save user-specific URL centrally
            SharedPreferences sharedPreferences = getSharedPreferences("UserUrls", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            String url = getUrlForUsername(username); // Define your logic to get URL based on username
            editor.putString(username, url);
            editor.apply();
        }

        private String getUrlForUsername(String username) {
            // Implement your logic to get URL based on username
            // For now, returning a dummy URL
            return "https://example.com/" + username;
        }
    }
