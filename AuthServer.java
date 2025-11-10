import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI; // New import for HttpClient
import java.net.http.HttpClient; // New import for HttpClient
import java.net.http.HttpRequest; // New import for HttpClient
import java.net.http.HttpResponse; // New import for HttpClient
import java.util.concurrent.Executors;

public class AuthServer {

    // --- IMPORTANT: Replace with your actual Firebase Web API Key ---
    // You can find this in Firebase Console -> Project settings -> General tab -> Web API Key
	private static final String FIREBASE_WEB_API_KEY = System.getenv("FIREBASE_WEB_API_KEY");
    // Re-use HttpClient for outgoing requests to Google Identity Platform
    private static final HttpClient identityPlatformHttpClient = HttpClient.newHttpClient();

    public static void main(String[] args) throws IOException {
    	if (FIREBASE_WEB_API_KEY == null || FIREBASE_WEB_API_KEY.isEmpty()) {
    	    System.err.println("ERROR: FIREBASE_WEB_API_KEY environment variable is not set. Cannot proceed with Firebase Identity Platform calls.");
    	    // You might want to exit here:
    	    // System.exit(1);
    	}
        // --- STEP 1: INITIALIZE FIREBASE ADMIN SDK ---
        try {
            InputStream serviceAccount = AuthServer.class.getClassLoader().getResourceAsStream("serviceAccountKey.json");

            if (serviceAccount == null) {
                System.err.println("Error: serviceAccountKey.json not found in src/main/resources. Please place it there.");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            System.out.println("Firebase Admin SDK initialized successfully for project PA-3.");

        } catch (IOException e) {
            System.err.println("Failed to initialize Firebase Admin SDK: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // --- STEP 2: START YOUR HTTP SERVER AND DEFINE ENDPOINTS ---
     // Cloud Run injects the port via the PORT environment variable
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080")); // Default to 8080 for local testing
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0); // Listen on all interfaces


        // Endpoint for Signing Up New Users (unchanged)
        server.createContext("/api/auth/signup", (exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String email = extractFromJson(requestBody, "email");
                String password = extractFromJson(requestBody, "password");

                try {
                    UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                            .setEmail(email)
                            .setPassword(password)
                            .setEmailVerified(false)
                            .setDisabled(false);

                    UserRecord userRecord = FirebaseAuth.getInstance().createUser(request);
                    String response = "{\"message\": \"User " + userRecord.getUid() + " created successfully.\"}";
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    System.out.println("New user signed up: " + userRecord.getUid());

                } catch (FirebaseAuthException e) {
                    String errorResponse = "{\"error\": \"" + e.getMessage() + "\"}";
                    exchange.sendResponseHeaders(400, errorResponse.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(errorResponse.getBytes());
                    os.close();
                    System.err.println("Error signing up user: " + e.getMessage());
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }));

     // Endpoint for Sending Password Reset Emails (REVISED)
        server.createContext("/api/auth/reset-password", (exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String email = extractFromJson(requestBody, "email");

                // --- NEW: Call Firebase Identity Platform REST API directly ---

                // Check if the API key is set
                if (FIREBASE_WEB_API_KEY.equals("YOUR_FIREBASE_WEB_API_KEY") || FIREBASE_WEB_API_KEY.isEmpty()) {
                    String errorResponse = "{\"error\": \"Firebase Web API Key is not configured in AuthServer.java.\"}";
                    exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(errorResponse.getBytes());
                    os.close();
                    System.err.println("ERROR: FIREBASE_WEB_API_KEY is not set.");
                    return;
                }

                // URL for Firebase Identity Platform's sendOobCode (Out-of-Band Code) endpoint
                String identityPlatformUrl = String.format(
                    "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=%s",
                    FIREBASE_WEB_API_KEY
                );

                // Request body for Firebase Identity Platform to request a password reset email
                String identityRequestBody = String.format(
                    "{\"requestType\":\"PASSWORD_RESET\",\"email\":\"%s\"}",
                    email
                );

                try {
                    HttpRequest identityRequest = HttpRequest.newBuilder()
                        .uri(URI.create(identityPlatformUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(identityRequestBody))
                        .build();

                    HttpResponse<String> identityResponse = identityPlatformHttpClient.send(identityRequest, HttpResponse.BodyHandlers.ofString());
                    String identityResponseBody = identityResponse.body();

                    if (identityResponse.statusCode() == 200) {
                        // Success: Firebase has been instructed to send the email
                        String response = "{\"message\": \"Password reset email successfully requested from Firebase for " + email + ".\"}";
                        exchange.sendResponseHeaders(200, response.getBytes().length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                        System.out.println("Password reset email requested for: " + email);
                    } else {
                        // Error: Extract the error message from the Identity Platform's response
                        String firebaseErrorMessage = extractNestedFromJson(identityResponseBody, "error", "message");
                        String errorResponse = String.format("{\"error\": \"%s\"}", firebaseErrorMessage != null ? firebaseErrorMessage : "Unknown error requesting password reset from Firebase.");
                        exchange.sendResponseHeaders(identityResponse.statusCode(), errorResponse.getBytes().length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(errorResponse.getBytes());
                        os.close();
                        System.err.println("Error requesting password reset from Firebase for " + email + ": " + identityResponseBody);
                    }

                } catch (IOException | InterruptedException e) {
                    String errorResponse = String.format("{\"error\": \"Network error contacting Firebase Identity Platform: %s\"}", e.getMessage());
                    exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(errorResponse.getBytes());
                    os.close();
                    System.err.println("Exception during password reset request to Firebase Identity Platform: " + e.getMessage());
                }

                // --- END NEW: Call Firebase Identity Platform REST API directly ---

            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }));

        // ... (rest of AuthServer.java, including extractFromJson and extractNestedFromJson) ...

        // --- REVISED: Endpoint for Signing In Existing Users ---
        server.createContext("/api/auth/signin", (exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                String email = extractFromJson(requestBody, "email");
                String password = extractFromJson(requestBody, "password");

                // Check if the API key is set
                if (FIREBASE_WEB_API_KEY.equals("YOUR_FIREBASE_WEB_API_KEY") || FIREBASE_WEB_API_KEY.isEmpty()) {
                    String errorResponse = "{\"error\": \"Firebase Web API Key is not configured in AuthServer.java.\"}";
                    exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(errorResponse.getBytes());
                    os.close();
                    System.err.println("ERROR: FIREBASE_WEB_API_KEY is not set.");
                    return;
                }

                // URL for Firebase Identity Platform's signInWithPassword endpoint
                String identityPlatformUrl = String.format(
                    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=%s",
                    FIREBASE_WEB_API_KEY
                );

                // Request body for Firebase Identity Platform
                String identityRequestBody = String.format(
                    "{\"email\":\"%s\",\"password\":\"%s\",\"returnSecureToken\":true}",
                    email, password
                );

                try {
                    HttpRequest identityRequest = HttpRequest.newBuilder()
                        .uri(URI.create(identityPlatformUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(identityRequestBody))
                        .build();

                    HttpResponse<String> identityResponse = identityPlatformHttpClient.send(identityRequest, HttpResponse.BodyHandlers.ofString());
                    String identityResponseBody = identityResponse.body();

                    if (identityResponse.statusCode() == 200) {
                        // Success: Extract idToken from Identity Platform's response
                        String firebaseIdToken = extractFromJson(identityResponseBody, "idToken");
                        // We also get a refreshToken, but for this example, idToken is enough.
                        String finalResponse = String.format(
                            "{\"message\": \"Login successful!\", \"firebaseIdToken\": \"%s\"}",
                            firebaseIdToken
                        );
                        exchange.sendResponseHeaders(200, finalResponse.getBytes().length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(finalResponse.getBytes());
                        os.close();
                        System.out.println("User signed in: " + email);
                    } else {
                        // Error: Extract the error message from the Identity Platform's response
                        // Identity Platform error format is typically {"error": {"code": 400, "message": "EMAIL_NOT_FOUND", "errors": [...]}}
                        String firebaseErrorMessage = extractNestedFromJson(identityResponseBody, "error", "message");
                        String errorResponse = String.format("{\"error\": \"%s\"}", firebaseErrorMessage != null ? firebaseErrorMessage : "Unknown error during sign-in.");
                        exchange.sendResponseHeaders(identityResponse.statusCode(), errorResponse.getBytes().length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(errorResponse.getBytes());
                        os.close();
                        System.err.println("Error signing in user " + email + ": " + identityResponseBody);
                    }

                } catch (IOException | InterruptedException e) {
                    String errorResponse = String.format("{\"error\": \"Network error contacting Firebase Identity Platform: %s\"}", e.getMessage());
                    exchange.sendResponseHeaders(500, errorResponse.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(errorResponse.getBytes());
                    os.close();
                    System.err.println("Exception during sign-in to Firebase Identity Platform: " + e.getMessage());
                }

            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }));

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        System.out.println("Firebase Auth Backend Server started on port 8080.");
    }

    // Very basic JSON extractor. Use a library like Gson/Jackson in production.
    private static String extractFromJson(String json, String key) {
        String search = "\"" + key + "\": \"";
        int startIndex = json.indexOf(search);
        if (startIndex == -1) return null;
        startIndex += search.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) return null;
        return json.substring(startIndex, endIndex);
    }

    // Helper for extracting nested JSON values (e.g., "error.message")
    private static String extractNestedFromJson(String json, String parentKey, String childKey) {
        String parentSearch = "\"" + parentKey + "\":";
        int parentStartIndex = json.indexOf(parentSearch);
        if (parentStartIndex == -1) return null;

        int braceStartIndex = json.indexOf("{", parentStartIndex + parentSearch.length());
        if (braceStartIndex == -1) return null;
        int braceEndIndex = json.indexOf("}", braceStartIndex);
        if (braceEndIndex == -1) return null;

        String parentJson = json.substring(braceStartIndex, braceEndIndex + 1);
        return extractFromJson(parentJson, childKey);
    }
}
