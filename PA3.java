import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import javax.swing.*;

public class PA3 extends JFrame {
    private JPanel panel = new JPanel();
    private JButton back = new JButton("Back");
    private JButton quit = new JButton("Quit");
    private JButton options = new JButton("Options");
    private JPanel currentContentPanel = new JPanel();

    // Forgot Password specific button
    private JButton forgotP = new JButton("Forgot Password");
    private JButton forgotPSubmit = new JButton("Submit Reset Request"); // Renamed for clarity

    // Login fields
    private JButton submit = new JButton("Submit");
    private JTextField loginEmailBox = new JTextField();
    private JPasswordField loginPasswordBox = new JPasswordField();

    // Sign Up/Forgot Password Email Box (reused for input)
    private JTextField emailInputBox = new JTextField(); // Consolidated for clarity (signup and forgot password email)
    private JPasswordField signupPasswordBox = new JPasswordField(); // Only for signup

    // Sign Up fields
    private JButton createAcc = new JButton("Create Account");
    private JButton signupButton = new JButton("Create Account"); // Button to navigate to signup screen

    private JLabel success = new JLabel("Success."); // Consider removing or making more dynamic

    // --- Backend Communication Constants ---
    private static final String BACKEND_BASE_URL = "https://auth-backend-service-286302963427.us-central1.run.app/api/auth";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    //hiraganakatakana deck stuff
    private JButton play = new JButton("Play");
	private JButton next = new JButton("Next");
	private JButton back2 = new JButton("Back");
	private JLabel fillerspace = new JLabel("");
	private int min;
	private double corr = 0;
	private int max = 5;
	private boolean hirakata = true;

    public PA3() {
        intro();
        add(panel);

        // --- Common Listeners ---
        back.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                panel.removeAll();
                intro();
                panel.revalidate();
                panel.repaint();
            }
        });

        quit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        options.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                panel.removeAll();
                panel.setLayout(new GridLayout(4, 1, 1, 1));

                panel.add(forgotP);
                panel.add(signupButton);
                panel.add(back);
                panel.add(quit);

                panel.revalidate();
                panel.repaint();
            }
        });

        // --- Sign Up UI Navigation ---
        signupButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                panel.removeAll();

                JLabel signupLabel = new JLabel("Sign up");
                JLabel emailLabel = new JLabel("Email:");
                JLabel passwordLabel = new JLabel("Password:");
                JLabel blank = new JLabel("");

                emailInputBox.setText(""); // Clear previous input
                signupPasswordBox.setText(""); // Clear previous input

                panel.setLayout(new GridLayout(4, 2, 1, 1));
                panel.add(blank);
                panel.add(signupLabel);
                panel.add(emailLabel);
                panel.add(emailInputBox); // Reusing emailInputBox
                panel.add(passwordLabel);
                panel.add(signupPasswordBox);
                panel.add(options);
                panel.add(createAcc);

                panel.revalidate();
                panel.repaint();
            }
        });

        // --- Forgot Password UI Navigation ---
        forgotP.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                panel.removeAll();

                JLabel forgotPLabel = new JLabel("Forgot Password");
                JLabel emailLabel = new JLabel("Enter your email:");
                JLabel blank = new JLabel("");

                emailInputBox.setText(""); // Clear previous input

                panel.setLayout(new GridLayout(3, 2, 1, 1));
                panel.add(blank);
                panel.add(forgotPLabel);
                panel.add(emailLabel);
                panel.add(emailInputBox); // Reusing emailInputBox
                panel.add(back); // Option to go back
                panel.add(forgotPSubmit); // Submit button for password reset

                panel.revalidate();
                panel.repaint();
            }
        });

        // --- Create Account (Sign Up) Logic ---
        createAcc.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String email = emailInputBox.getText().trim();
                String password = new String(signupPasswordBox.getPassword());
                signupPasswordBox.setText("");

                if (email.isEmpty() || password.isEmpty()) {
                    JOptionPane.showMessageDialog(panel, "Email and password cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                sendAuthRequest("/signup", email, password)
                    .thenAccept(responseBody -> {
                        SwingUtilities.invokeLater(() -> {
                            String message = extractFromJson(responseBody, "message");
                            if (message != null) {
                                JOptionPane.showMessageDialog(panel, message, "Sign Up Success", JOptionPane.INFORMATION_MESSAGE);
                                panel.removeAll();
                                intro();
                                panel.revalidate();
                                panel.repaint();
                            } else {
                                String error = extractFromJson(responseBody, "error");
                                JOptionPane.showMessageDialog(panel, error != null ? error : "Unknown sign up error.", "Sign Up Error", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    })
                    .exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(panel, "Network or server error during sign up: " + ex.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
                        });
                        return null;
                    });
            }
        });

        // --- Forgot Password Submission Logic ---
        forgotPSubmit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String email = emailInputBox.getText().trim();
                emailInputBox.setText("");

                if (email.isEmpty()) {
                    JOptionPane.showMessageDialog(panel, "Email cannot be empty for password reset.", "Input Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // CORRECT: Your client PA3.java should call its backend (AuthServer)
                sendAuthRequest("/reset-password", email) // <--- Call your backend!
                    .thenAccept(responseBody -> {
                        SwingUtilities.invokeLater(() -> {
                            String message = extractFromJson(responseBody, "message");
                            if (message != null) {
                                JOptionPane.showMessageDialog(panel, message, "Password Reset Link Sent", JOptionPane.INFORMATION_MESSAGE);
                                panel.removeAll();
                                intro(); // Go back to login screen after sending
                                panel.revalidate();
                                panel.repaint();
                            } else {
                                String error = extractFromJson(responseBody, "error");
                                JOptionPane.showMessageDialog(panel, error != null ? error : "Unknown error sending reset email.", "Password Reset Error", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    })
                    .exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(panel, "Network or server error during password reset request: " + ex.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
                        });
                        return null;
                    });
            }
        });


        // --- Login Submission Logic ---
        submit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String email = loginEmailBox.getText().trim();
                String password = new String(loginPasswordBox.getPassword());
                loginPasswordBox.setText("");

                if (email.isEmpty() || password.isEmpty()) {
                    JOptionPane.showMessageDialog(panel, "Email and password cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                sendAuthRequest("/signin", email, password)
                .thenAccept(responseBody -> {
                    SwingUtilities.invokeLater(() -> {
                        String message = extractFromJson(responseBody, "message");
                        String firebaseIdToken = extractFromJson(responseBody, "firebaseIdToken");
                        if (message != null && firebaseIdToken != null) {
                            JOptionPane.showMessageDialog(currentContentPanel, "Login Success!", "Login Success", JOptionPane.INFORMATION_MESSAGE);
                            // >>>>>>>>>>>>>>>>>>>>>> INTEGRATE GAME HERE <<<<<<<<<<<<<<<<<<<<<<<<
                            // Create the game panel, passing a callback to return to login
                            //HiraganaKatakana gamePanel = new HiraganaKatakana(v -> intro());
                            //gamePanel.intro(); // Display the game panel
                            panel.removeAll();
                            intro2();//sends to the HiraganaKatakana deck!!!
                            panel.revalidate();
                            panel.repaint();
                            // >>>>>>>>>>>>>>>>>>>>>> END GAME INTEGRATION <<<<<<<<<<<<<<<<<<<<<<<<
                        } else {
                            String error = extractFromJson(responseBody, "error");
                            JOptionPane.showMessageDialog(currentContentPanel, error != null ? error : "Unknown login error.", "Login Error", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(currentContentPanel, "Network or server error during login: " + ex.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
                    });
                    return null;
                });
                
            }
        });
        
        //HiraganaKatakana Deck for after logging in
        //buttons
      		play.addActionListener(new ActionListener() {
      			public void actionPerformed(ActionEvent e) {
      				panel.removeAll();// clears panel
      				if (hirakata)// selects the alphabet the user wants to use
      				{
      					Hiragana();
      				} else {
      					Katakana();
      				}
      				panel.revalidate();
                      panel.repaint();// resets visibility
      			}
      		});
      		next.addActionListener(new ActionListener() {
      			public void actionPerformed(ActionEvent e) {
      				panel.removeAll();

      				if (min < max)// if max amount of questions has not been reached
      				{
      					if (hirakata) {
      						Hiragana();

      					} else {
      						Katakana();

      					}
      					System.out.println(min + "/" + max);// prints the question number to console
      					panel.setVisible(false);
      					panel.setVisible(true);
      				} else {
      					System.out.println(min + "/" + max);
      					System.out.println("Completed!");
      					panel.setLayout(new GridLayout(2, 1, 1, 1));
      					double acc = (corr / min) * 100;
      					JLabel re = new JLabel("Test Accuracy: " + acc + "%");
      					panel.add(re);
      					panel.add(back2);
      					//add(results);
      					
      					panel.revalidate();
      	                panel.repaint();
      				}

      			}
      		});
      		back2.addActionListener(new ActionListener() {
      			public void actionPerformed(ActionEvent e) {
      				panel.removeAll();
      				//results.removeAll();
      				//remove(results);
      				intro2();
      				panel.revalidate();
      				panel.repaint();
      			}
      		});
    }
    
    // --- UI Layout for Intro (Login) Screen ---
    public void intro() {
        panel.removeAll();
        panel.setLayout(new GridLayout(4, 2, 1, 1));

        JLabel label = new JLabel("Login");
        JLabel emailLabel = new JLabel("Email:");
        JLabel passwordLabel = new JLabel("Password:");
        JLabel blank = new JLabel("");

        loginEmailBox.setText(""); // Clear previous input
        loginPasswordBox.setText(""); // Clear previous input

        panel.add(blank);
        panel.add(label);
        panel.add(emailLabel);
        panel.add(loginEmailBox);
        panel.add(passwordLabel);
        panel.add(loginPasswordBox);
        panel.add(options);
        panel.add(submit);

        panel.revalidate();
        panel.repaint();
    }

    // --- HTTP Request Helper (for email & password) ---
    private CompletableFuture<String> sendAuthRequest(String endpoint, String email, String password) {
        String jsonPayload = String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password);
        return httpClient.sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create(BACKEND_BASE_URL + endpoint))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(HttpResponse::body);
    }

    // --- HTTP Request Helper (for email only, e.g., password reset) ---
    private CompletableFuture<String> sendAuthRequest(String endpoint, String email) {
        String jsonPayload = String.format("{\"email\": \"%s\"}", email);
        return httpClient.sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create(BACKEND_BASE_URL + endpoint))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(HttpResponse::body);
    }

    // --- Very basic JSON extractor for demo purposes. Use Gson/Jackson in production. ---
    private static String extractFromJson(String json, String key) {
        String search = "\"" + key + "\": \"";
        int startIndex = json.indexOf(search);
        if (startIndex == -1) return null;
        startIndex += search.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) return null;
        return json.substring(startIndex, endIndex);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PA3 frame = new PA3();
            frame.setSize(500, 500);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setTitle("PA3 Login");
            frame.setVisible(true);
        });
    }
    
    //hiraganakatakana deck stuff
    public void intro2() {
		panel.setLayout(new GridLayout(3, 1, 1, 1));
		min = 0;
		corr = 0;
		panel.add(play);
		JButton settings = new JButton("Settings");
		JLabel in = new JLabel("# of Questions:");
		JButton five = new JButton("Five");
		JButton ten = new JButton("Ten");
		JButton fifthteen = new JButton("Fifthteen");
		JButton twenty = new JButton("Twenty");
		JLabel in2 = new JLabel("Japanese Alphabet:");
		JButton hira = new JButton("Hiragana");
		JButton kata = new JButton("Katakana");
		panel.add(settings);
		panel.add(quit);
		settings.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				panel.removeAll();
				panel.setLayout(new GridLayout(5, 5, 1, 1));
				panel.add(in);
				panel.add(fillerspace);
				panel.add(five);
				panel.add(ten);
				panel.add(fifthteen);
				panel.add(twenty);
				panel.add(in2);
				panel.add(hira);
				panel.add(kata);
				panel.add(back2);
				panel.setVisible(false);
				panel.setVisible(true);
			}
		});
		five.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				max = 5;
			}
		});
		ten.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				max = 10;
			}
		});
		fifthteen.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				max = 15;
			}
		});
		twenty.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				max = 20;
			}
		});
		hira.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				hirakata = true;
			}
		});
		kata.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				hirakata = false;
			}
		});
		add(panel);
	}

	public void Hiragana() {
		panel.setLayout(new GridLayout(3, 5, 1, 1));

		List<String> myStrings = List.of("a", "i", "u", "e", "o", "ka", "ki", "ku", "ke", "ko", "sa", "shi", "su", "se",
				"so", "ta", "chi", "tsu", "te", "to", "na", "ni", "nu", "ne", "no", "ha", "hi", "fu", "he", "ho", "ma",
				"mi", "mu", "me", "mo", "ya", "yu", "yo", "ra", "ri", "ru", "re", "ro", "wa", "wo", "n");
		Random random = new Random();

		List<String> myHira = List.of("あ", "い", "う", "え", "お", // origin
				"か", "き", "く", "け", "こ", // K
				"さ", "し", "す", "せ", "そ", // S
				"た", "ち", "つ", "て", "と", // T
				"な", "に", "ぬ", "ね", "の", // N
				"は", "ひ", "ふ", "へ", "ほ", // H
				"ま", "み", "む", "め", "も", // M
				"や", "ゆ", "よ", // Y
				"ら", "り", "る", "れ", "ろ", // R
				"わ", "を", // W
				"ん");// N
		String ans = myStrings.get(random.nextInt(myStrings.size()));
		String f = myStrings.get(random.nextInt(myStrings.size()));
		String f2 = myStrings.get(random.nextInt(myStrings.size()));
		String f3 = myStrings.get(random.nextInt(myStrings.size()));
		while (f.equals(ans) || f2.equals(ans) || f3.equals(ans) || f.equals(f2) || f.equals(f3) || f2.equals(f3))// makes
																													// sure
																													// no
																													// identical
																													// buttons
		{
			f = myStrings.get(random.nextInt(myStrings.size()));
			f2 = myStrings.get(random.nextInt(myStrings.size()));
			f3 = myStrings.get(random.nextInt(myStrings.size()));
		}
		String ans2 = "";
		switch (ans) {
		case "a":
			ans2 = "あ";
			break;
		case "i":
			ans2 = "い";
			break;
		case "u":
			ans2 = "う";
			break;
		case "e":
			ans2 = "え";
			break;
		case "o":
			ans2 = "お";
			break;
		// K
		case "ka":
			ans2 = "か";
			break;
		case "ki":
			ans2 = "き";
			break;
		case "ku":
			ans2 = "く";
			break;
		case "ke":
			ans2 = "け";
			break;
		case "ko":
			ans2 = "こ";
			break;
		// S
		case "sa":
			ans2 = "さ";
			break;
		case "shi":
			ans2 = "し";
			break;
		case "su":
			ans2 = "す";
			break;
		case "se":
			ans2 = "せ";
			break;
		case "so":
			ans2 = "そ";
			break;
		// T
		case "ta":
			ans2 = "た";
			break;
		case "chi":
			ans2 = "ち";
			break;
		case "tsu":
			ans2 = "つ";
			break;
		case "te":
			ans2 = "て";
			break;
		case "to":
			ans2 = "と";
			break;
		// N
		case "na":
			ans2 = "な";
			break;
		case "ni":
			ans2 = "に";
			break;
		case "nu":
			ans2 = "ぬ";
			break;
		case "ne":
			ans2 = "ね";
			break;
		case "no":
			ans2 = "の";
			break;
		// H
		case "ha":
			ans2 = "は";
			break;
		case "hi":
			ans2 = "ひ";
			break;
		case "fu":
			ans2 = "ふ";
			break;
		case "he":
			ans2 = "へ";
			break;
		case "ho":
			ans2 = "ほ";
			break;
		// M
		case "ma":
			ans2 = "ま";
			break;
		case "mi":
			ans2 = "み";
			break;
		case "mu":
			ans2 = "む";
			break;
		case "me":
			ans2 = "め";
			break;
		case "mo":
			ans2 = "も";
			break;
		// Y
		case "ya":
			ans2 = "や";
			break;
		case "yu":
			ans2 = "ゆ";
			break;
		case "yo":
			ans2 = "よ";
			break;
		// R
		case "ra":
			ans2 = "ら";
			break;
		case "ri":
			ans2 = "り";
			break;
		case "ru":
			ans2 = "る";
			break;
		case "re":
			ans2 = "れ";
			break;
		case "ro":
			ans2 = "ろ";
			break;
		// W
		case "wa":
			ans2 = "わ";
			break;
		case "wo":
			ans2 = "を";
			break;
		// N
		case "n":
			ans2 = "ん";
			break;
		}

		JLabel p2 = new JLabel(ans2);
		p2.setFont(new Font("", Font.PLAIN, 20));
		JLabel fillerspace = new JLabel("");
		JButton rb = new JButton(ans);
		JButton rb2 = new JButton(f);
		JButton rb3 = new JButton(f2);
		JButton rb4 = new JButton(f3);
		panel.add(fillerspace);
		panel.add(p2, BorderLayout.CENTER);
		Random x = new Random();
		int randomizer = x.nextInt(4);
		switch (randomizer)// randomizes the placement of the buttons
		{
		case 0:
			panel.add(rb);
			panel.add(rb2);
			panel.add(rb3);
			panel.add(rb4);
			break;
		case 1:
			panel.add(rb2);
			panel.add(rb);
			panel.add(rb3);
			panel.add(rb4);
			break;
		case 2:
			panel.add(rb3);
			panel.add(rb2);
			panel.add(rb);
			panel.add(rb4);
			break;
		case 3:
			panel.add(rb4);
			panel.add(rb2);
			panel.add(rb3);
			panel.add(rb);
			break;
		}
		JLabel p = new JLabel("Correct");
		p.setOpaque(true);
		p.setBackground(Color.GREEN);
		JLabel p3 = new JLabel("Incorrect. Correct answer: " + ans);
		p3.setForeground(Color.RED);

		rb.addActionListener(// correct button/answer
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						panel.removeAll();
						panel.add(p);
						panel.add(next);
						panel.add(back2);
						panel.setVisible(false);
						panel.setVisible(true);
						min++;
						corr++;
					}
				});
		rb2.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				panel.removeAll();
				panel.add(p3);
				panel.add(next);
				panel.add(back2);
				panel.setVisible(false);
				panel.setVisible(true);
				min++;
			}
		});
		rb3.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				panel.removeAll();
				panel.add(p3);
				panel.add(next);
				panel.add(back2);
				panel.setVisible(false);
				panel.setVisible(true);
				min++;
			}
		});
		rb4.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				panel.removeAll();
				panel.add(p3);
				panel.add(next);
				panel.add(back2);
				panel.setVisible(false);
				panel.setVisible(true);
				min++;
			}
		});
		add(panel);
	}

	public void Katakana()// katakana
	{
		panel.setLayout(new GridLayout(3, 5, 1, 1));

		List<String> myStrings = List.of("a", "i", "u", "e", "o", "ka", "ki", "ku", "ke", "ko", "sa", "shi", "su", "se",
				"so", "ta", "chi", "tsu", "te", "to", "na", "ni", "nu", "ne", "no", "ha", "hi", "fu", "he", "ho", "ma",
				"mi", "mu", "me", "mo", "ya", "yu", "yo", "ra", "ri", "ru", "re", "ro", "wa", "wo", "n");

		Random random = new Random();

		List<String> myKata = List.of("ア", "イ", "ウ", "エ", "オ", // origin
				"カ", "キ", "ク", "ケ", "コ", // K
				"サ", "シ", "ス", "セ", "ソ", // S
				"タ", "チ", "ツ", "テ", "ト", // T
				"ナ", "ニ", "ヌ", "ネ", "ノ", // Naa
				"ハ", "ヒ", "フ", "ヘ", "ホ", // H
				"マ", "ミ", "ム", "メ", "モ", // M
				"ヤ", "ユ", "ヨ", // Y
				"ラ", "リ", "ル", "レ", "ロ", // R
				"ワ", "ヲ", // W
				"ン");// N

		String ans = myStrings.get(random.nextInt(myStrings.size()));
		String f = myStrings.get(random.nextInt(myStrings.size()));
		String f2 = myStrings.get(random.nextInt(myStrings.size()));
		String f3 = myStrings.get(random.nextInt(myStrings.size()));
		while (f.equals(ans) || f2.equals(ans) || f3.equals(ans) || f.equals(f2) || f.equals(f3) || f2.equals(f3))// makes
																													// sure
																													// no
																													// identical
																													// buttons
		{
			f = myStrings.get(random.nextInt(myStrings.size()));
			f2 = myStrings.get(random.nextInt(myStrings.size()));
			f3 = myStrings.get(random.nextInt(myStrings.size()));
		}
		String ans2 = "";
		switch (ans) {
		case "a":
			ans2 = "ア";
			break;
		case "i":
			ans2 = "イ";
			break;
		case "u":
			ans2 = "ウ";
			break;
		case "e":
			ans2 = "エ";
			break;
		case "o":
			ans2 = "オ";
			break;
		// K
		case "ka":
			ans2 = "カ";
			break;
		case "ki":
			ans2 = "キ";
			break;
		case "ku":
			ans2 = "ク";
			break;
		case "ke":
			ans2 = "ケ";
			break;
		case "ko":
			ans2 = "コ";
			break;
		// S
		case "sa":
			ans2 = "サ";
			break;
		case "shi":
			ans2 = "シ";
			break;
		case "su":
			ans2 = "ス";
			break;
		case "se":
			ans2 = "セ";
			break;
		case "so":
			ans2 = "ソ";
			break;
		// T
		case "ta":
			ans2 = "タ";
			break;
		case "chi":
			ans2 = "チ";
			break;
		case "tsu":
			ans2 = "ツ";
			break;
		case "te":
			ans2 = "テ";
			break;
		case "to":
			ans2 = "ト";
			break;
		// N
		case "na":
			ans2 = "ナ";
			break;
		case "ni":
			ans2 = "ニ";
			break;
		case "nu":
			ans2 = "ヌ";
			break;
		case "ne":
			ans2 = "ネ";
			break;
		case "no":
			ans2 = "ノ";
			break;
		// H
		case "ha":
			ans2 = "ハ";
			break;
		case "hi":
			ans2 = "ヒ";
			break;
		case "fu":
			ans2 = "フ";
			break;
		case "he":
			ans2 = "ヘ";
			break;
		case "ho":
			ans2 = "ホ";
			break;
		// M
		case "ma":
			ans2 = "マ";
			break;
		case "mi":
			ans2 = "ミ";
			break;
		case "mu":
			ans2 = "ム";
			break;
		case "me":
			ans2 = "メ";
			break;
		case "mo":
			ans2 = "モ";
			break;
		// Y
		case "ya":
			ans2 = "ヤ";
			break;
		case "yu":
			ans2 = "ユ";
			break;
		case "yo":
			ans2 = "ヨ";
			break;
		// R
		case "ra":
			ans2 = "ラ";
			break;
		case "ri":
			ans2 = "リ";
			break;
		case "ru":
			ans2 = "ル";
			break;
		case "re":
			ans2 = "レ";
			break;
		case "ro":
			ans2 = "ロ";
			break;
		// W
		case "wa":
			ans2 = "ワ";
			break;
		case "wo":
			ans2 = "ヲ";
			break;
		// N
		case "n":
			ans2 = "ン";
			break;
		}

		JLabel p2 = new JLabel(ans2);
		p2.setFont(new Font("", Font.PLAIN, 20));
		JLabel fillerspace = new JLabel("");
		JButton rb = new JButton(ans);
		JButton rb2 = new JButton(f);
		JButton rb3 = new JButton(f2);
		JButton rb4 = new JButton(f3);
		panel.add(fillerspace);
		panel.add(p2, BorderLayout.CENTER);
		Random x = new Random();
		int randomizer = x.nextInt(4);
		switch (randomizer) {
		case 0:
			panel.add(rb);
			panel.add(rb2);
			panel.add(rb3);
			panel.add(rb4);
			break;
		case 1:
			panel.add(rb2);
			panel.add(rb);
			panel.add(rb3);
			panel.add(rb4);
			break;
		case 2:
			panel.add(rb3);
			panel.add(rb2);
			panel.add(rb);
			panel.add(rb4);
			break;
		case 3:
			panel.add(rb4);
			panel.add(rb2);
			panel.add(rb3);
			panel.add(rb);
			break;
		}
		JLabel p = new JLabel("Correct");
		p.setOpaque(true);
		p.setBackground(Color.GREEN);
		JLabel p3 = new JLabel("Incorrect. Correct answer: " + ans);
		p3.setForeground(Color.RED);

		rb.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				panel.removeAll();
				panel.add(p);
				panel.add(next);
				panel.add(back2);
				panel.setVisible(false);
				panel.setVisible(true);
				min++;
				corr++;
			}
		});
		rb2.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				panel.removeAll();
				panel.add(p3);
				panel.add(next);
				panel.add(back2);
				panel.setVisible(false);
				panel.setVisible(true);
				min++;
			}
		});
		rb3.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				panel.removeAll();
				panel.add(p3);
				panel.add(next);
				panel.add(back2);
				panel.setVisible(false);
				panel.setVisible(true);
				min++;
			}
		});
		rb4.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				panel.removeAll();
				panel.add(p3);
				panel.add(next);
				panel.add(back2);
				panel.setVisible(false);
				panel.setVisible(true);
				min++;
			}
		});
		add(panel);
	}
}
