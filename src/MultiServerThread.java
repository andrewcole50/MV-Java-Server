import com.google.gson.*;
import com.rocketsoftware.mvapi.MVConnection;
import com.rocketsoftware.mvapi.MVConstants;
import com.rocketsoftware.mvapi.MVSubroutine;
import com.rocketsoftware.mvapi.ResultSet.MVResultSet;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;


class MultiServerThread extends Thread {

	private Socket socket;
	private JsonParser parser;

	MultiServerThread(Socket socket) {
		super("MultiServerThread");
		this.socket = socket;
		this.parser = new JsonParser();
	}

	@Override
	public void run() {
		try (
				PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))
		) {
			String input;
			while (true) {
				StringBuilder sb = new StringBuilder();
				String character;
				while (!(character = Character.toString((char) in.read())).equals(Character.toString((char) 3))) {
					sb.append(character);
				}
				input = sb.toString();

				MVConnection mvConn = null;
				try {
					mvConn = Main.pool.borrowObject();
					MVSubroutine sub;

					Gson gson = new Gson();
					JsonObject json = parser.parse(input).getAsJsonObject();

					String command = json.get("command").getAsString().toUpperCase();
					String file, key, data;
					int attribute;

					String response;
					switch (command) {
						case "JREAD":
							file = json.get("file").getAsString().toUpperCase();
							key = json.get("key").getAsString();

							sub = mvConn.mvSub("JREAD", 4);
							sub.setArg(0, file);
							sub.setArg(1, key);
							sub.mvCall();

							if (sub.getArg(3).equals("1")) {
								response = gson.toJson(false);
							} else {
								response = gson.toJson(sub.getArg(2));
							}
							System.out.println(sub.getArg(2));
							break;
						case "JREADU":
							file = json.get("file").getAsString().toUpperCase();
							key = json.get("key").getAsString();

							sub = mvConn.mvSub("JREADU", 5);
							sub.setArg(0, file);
							sub.setArg(1, key);
							sub.mvCall();

							if (sub.getArg(3).equals("1")) {
								if (!sub.getArg(4).equals("0")) {
									Map<String, Object> lockMessage = new HashMap<>();
									lockMessage.put("error", true);
									lockMessage.put("lockedBy", sub.getArg(4));
									response = gson.toJson(lockMessage);
								} else {
									response = gson.toJson(false);
								}
							} else {
								response = gson.toJson(sub.getArg(2));
							}

							break;
						case "JREADV":
							file = json.get("file").getAsString().toUpperCase();
							key = json.get("key").getAsString();
							attribute = json.get("attribute").getAsInt();

							sub = mvConn.mvSub("JREADV", 5);
							sub.setArg(0, file);
							sub.setArg(1, key);
							sub.setArg(2, String.valueOf(attribute));
							sub.mvCall();

							if (sub.getArg(4).equals("1")) {
								response = gson.toJson(false);
							} else {
								response = gson.toJson(sub.getArg(3));
							}
							break;
						case "READV":
							file = json.get("file").getAsString().toUpperCase();
							key = json.get("key").getAsString();
							attribute = json.get("attribute").getAsInt();
							response = gson.toJson(mvConn.fileReadV(file, key, attribute));
							break;
						case "JWRITEV":
							file = json.get("file").getAsString().toUpperCase();
							key = json.get("key").getAsString();
							attribute = json.get("attribute").getAsInt();
							data = json.get("data").getAsString();
							data = sanitizeMarks(data);
							sub = mvConn.mvSub("JWRITE", 5);
							sub.setArg(0, file);
							sub.setArg(1, key);
							sub.setArg(2, data);
							sub.setArg(3, String.valueOf(attribute));
							sub.mvCall();
							response = gson.toJson(Boolean.valueOf(sub.getArg(4)));
							break;
						case "JWRITE":
							file = json.get("file").getAsString().toUpperCase();
							key = json.get("key").getAsString();
							data = json.get("data").getAsString();
							data = sanitizeMarks(data);
							sub = mvConn.mvSub("JWRITE", 4);
							sub.setArg(0, file);
							sub.setArg(1, key);
							sub.setArg(2, data);
							sub.mvCall();
							response = gson.toJson(!Boolean.valueOf(sub.getArg(3)));
							break;
						case "DELETE":
							file = json.get("file").getAsString().toUpperCase();
							key = json.get("key").getAsString();
							mvConn.fileDelete(file, key);
							response = gson.toJson(true);
							break;
						case "RELEASE":
							file = json.get("file").getAsString().toUpperCase();
							key = json.get("key").getAsString();
							mvConn.fileRelease(file, key);
							response = gson.toJson(true);
							break;
						case "SUB":
							List<String> array = new ArrayList<>();
							JsonArray arguments = json.get("arguments").getAsJsonArray();
							int length = arguments.size();
							sub = mvConn.mvSub(json.get("name").getAsString(), length);

							for (int i = 0; i < length; i++) {
								String argument = arguments.get(i).getAsString();
								sub.setArg(i, argument);
							}

							sub.mvCall();

							for (int i = 0; i < length; i++) {
								array.add(sub.getArg(i));
							}

							response = gson.toJson(array);

							break;
						case "QUERY":
							String query = json.get("query").getAsString();
							MVResultSet rs = mvConn.executeQuery(query);
							List<List<String>> resultSet = new ArrayList<>();
							while (rs.next()) {
								resultSet.add(new ArrayList<>(Arrays.asList(rs.getCurrentRow().split(MVConstants.AM))));
							}

							response = gson.toJson(resultSet);

							break;
						default:
							response = gson.toJson(false);
							break;
					}

					out.println(response + ((char) 3) + "\n");
					out.flush();
					out.close();
					socket.close();
					//this.wait(1000);
					Main.pool.returnObject(mvConn);
				} catch (Exception ex) {
					ex.printStackTrace();
					try {
						Main.pool.returnObject(mvConn);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		} catch (IOException e) {
			System.out.println("Socket Closed: " + socket.getInetAddress().toString());
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private String sanitizeMarks(String data) {
		data = data.replaceAll("~AM", MVConstants.AM);
		data = data.replaceAll("~VM", MVConstants.VM);
		data = data.replaceAll("~SM", MVConstants.SM);
		return data;
	}

	/*
	 * Untested!!
	 */
	private String convertArrayToString(JsonArray jsonArray) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < jsonArray.size(); i++) {
			JsonElement attribute = jsonArray.get(i);
			if (attribute.isJsonArray()) {
				JsonArray values = attribute.getAsJsonArray();
				for (int j = 0; j < values.size(); j++) {
					JsonElement value = values.get(j);
					if (value.isJsonArray()) {
						JsonArray subValues = value.getAsJsonArray();
						for (int k = 0; k < subValues.size(); k++) {
							sb.append(subValues.get(k).getAsString());

							if (k < subValues.size() - 1) sb.append(MVConstants.SM);
						}
					} else {
						sb.append(value.getAsString());
					}

					if (j < values.size()) sb.append(MVConstants.VM);
				}
			} else {
				sb.append(attribute.getAsString());
			}

			if (i < jsonArray.size() - 1) sb.append(MVConstants.AM);
		}

		return sb.toString();
	}
}
