import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONException;
import org.json.JSONObject;

// ctrl + shift + o -> import
public class ServerClass extends Thread {

	private Socket sock;
	private static ArrayList<Socket> clients = new ArrayList<Socket>(5);

	public ServerClass(Socket sock) {
		this.sock = sock;
	}

	public void remove(Socket socket) {
		for (Socket s : ServerClass.clients) {
			if (socket == s) {
				ServerClass.clients.remove(socket);
				break;
			}
		}
	}

	int EventId = 0;
	static String sqlstring = null;
	static String driver = null;
	static String DB_IP = null;
	static String DB_PORT = null;
	static String DB_NAME = null;
	static String DB_URL = null;

	static Socket client = null;
	static Connection conn = null;
	static Statement pstmt = null;
	static ResultSet rs = null;

	OutputStream out;
	PrintWriter writer;

	String[] workerIdArray;

	static java.util.Timer scheduler = new java.util.Timer();
	private final ReentrantLock lock = new ReentrantLock();
	public void run() {
		InputStream fromClient = null;
		OutputStream toClient = null;

		try {
			System.out.println(sock + ": 연결됨");

			// 연결된 클라이언트 소켓에 대한 인풋스트림 생성
			fromClient = sock.getInputStream();

			byte[] buf = new byte[1024];
			int count;

			// 일정 시간마다 사용자 테이블 데이터 불러오기
			// 추가된 데이터가 있다면 c#으로 내보내기
			java.util.TimerTask task = new java.util.TimerTask() {
				@Override
				public void run() {
					try {
						sqlstring = SelectWorkerId();
						PreparedStatement pstmt2 = conn.prepareStatement(sqlstring);

						ResultSet rs = pstmt2.executeQuery();
						workerIdArray = new String[1000];
						int i = 0;
						OutputStream out = client.getOutputStream();
						java.io.DataOutputStream outToClient = new java.io.DataOutputStream(client.getOutputStream());
						while (rs.next()) {
							workerIdArray[i] = rs.getString("WORKER_TAG") + "|" + rs.getString("WORKER_ID") + "|";
							// System.out.println(workerIdArray[i] + " \\" + i);
							byte[] ret = workerIdArray[i].getBytes();
							outToClient.write(ret);
							i++;
						}

						/*
						 * for (int k = 0; k < workerIdArray.length; k++) {
						 * System.out.println(workerIdArray[k]); //bytes = workerIdArray[k].getBytes();
						 * 
						 * } //System.out.println(new String(bytes));
						 */
						// writer.println(bytes);
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						//e.printStackTrace();
					}
				}
			};
			scheduler.scheduleAtFixedRate(task, 1000, 10000); // 1초 뒤 10초마다 반복실행
			// scheduler.cancel();

			// byte[] bytes = "asdf".getBytes();

			// 읽을 것이 없으면 가만히 대기상태에 빠짐 -1 상태가 아님
			// -1이란? ctrl + c 등의 중단 표시가 있을 경우 중단됨
			while ((count = fromClient.read(buf)) != -1) {
				for (Socket s : ServerClass.clients) {
					if (sock == s) {
						// 응답 메시지 보내기
						toClient = s.getOutputStream();
						toClient.write(buf, 0, count);
						toClient.flush();
					}
				}
				System.out.write(buf,0,count);
				 
				// 바이트배열을 json객체로 변환
				JSONObject jObject = new JSONObject(new String(buf));

				EventId++;

				String Id = "a" + Integer.toString(EventId);
				String Deviceid = jObject.getString("Device_id");
				String Workerid = jObject.getString("Worker_id");
				String Tag = jObject.getString("EPC");
				int Location = jObject.getInt("Location");
				String EventDate = jObject.getString("Date");
				int Flag = jObject.getInt("Flag");
				int Count = jObject.getInt("Count");
				int StockCount = jObject.getInt("StockCount");
				int InputCount = jObject.getInt("InputCount");
				int OutputCount = jObject.getInt("OutputCount");

				int resultCount = 0;
				
				Query(Id, Deviceid, Workerid, Tag, Location, EventDate, Flag, Count, StockCount, InputCount, OutputCount, resultCount);
				/*
				new Thread(() -> {
					Query(Id, Deviceid, Workerid, Tag, Location, EventDate, Flag, Count, StockCount, InputCount, OutputCount, resultCount);
				}).start();
				
				new Thread(() -> {
					Query(Id, Deviceid, Workerid, Tag, Location, EventDate, Flag, Count, StockCount, InputCount, OutputCount, resultCount);
				}).start();
				*/
				//Query(Id, Deviceid, Workerid, Tag, Location, EventDate, Flag, Count, StockCount, InputCount, OutputCount, resultCount);
			}
		} catch (IOException ex) {
			System.out.println(sock + ": 에러(" + ex + ")");
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				if (sock != null) {
					sock.close();
					// 접속 후 나가버린 클라이언트인 경우 ArrayList에서 제거
					remove(sock);
				}
				fromClient = null;
				toClient = null;
			} catch (IOException ex) {

			}
		}
	}
	
	public synchronized void Query(String Id, String Deviceid, String Workerid, String Tag, int Location, String EventDate,
			int Flag, int Count, int StockCount, int InputCount, int OutputCount, int resultCount)
	{
		lock.lock();
		try {

			pstmt = conn.createStatement();
			conn.setAutoCommit(false); // 자동 커밋

			sqlstring = insertData(Id, Deviceid, Workerid, Tag, Location, EventDate, Flag, Count);

			resultCount = pstmt.executeUpdate(sqlstring);
			if (resultCount != 1) {
				throw new Exception("insertData failed");
			}

			if (resultCount == 1) {
				sqlstring = insertData2(Id, Deviceid, Workerid, Tag, Location, EventDate, Flag, Count);

				resultCount = pstmt.executeUpdate(sqlstring);

			} else {
				throw new Exception("insertData2 failed");
			}

			if (true) {
				sqlstring = insertData3(Id, Deviceid, Workerid, Tag, Location, EventDate, Flag, Count);

				resultCount = pstmt.executeUpdate(sqlstring);

			}

			sqlstring = insertData4(Id, Deviceid, Workerid, Tag, Location, EventDate, Flag, Count, StockCount,
					InputCount, OutputCount);
			resultCount = pstmt.executeUpdate(sqlstring);

			conn.commit();
			System.out.println("성공적으로 추가되었음.");

		} catch (SQLException e) {
			System.out.println("error: " + e);
		}
		// System.out.println("EvnetDate : " + EventDate);
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
	}

	public String SelectWorkerId() {
		String sql = null;

		sql = "SELECT WORKER_TAG, WORKER_ID FROM tb_worker";

		// System.out.println(sql);

		return sql;
	}

	public String insertData(String TagId, String DeviceId, String WorkerId, String Tag, int Location, String TagTime,
			int Flag, int Count) {
		// timeCheck = java.sql.Date.valueOf(TagTime);
		String sql = null;
		if (Flag == 1) {
			// Query문 준비
			sql = "INSERT INTO tb_tag_input_history (TAG_ID, DEVICE_ID, WORKER_ID, TAG, LOCATION, TAG_TIME)"
					+ "VALUES('" + TagId + "', '" + DeviceId + "', '" + WorkerId + "', '" + Tag + "',  '" + Location
					+ "', '" + TagTime + "')";
			System.out.println(sql);
		} else if (Flag == 0) {
			// Query문 준비
			sql = "INSERT INTO tb_tag_output_history (ID,DEVICE_ID,WORKER_ID,TAG,LOCATION,TAG_TIME)" + "VALUES('"
					+ TagId + "', '" + DeviceId + "', '" + WorkerId + "', '" + Tag + "', '" + Location + "', '"
					+ TagTime + "')";
			System.out.println(sql);
		}

		// System.out.println(sql);

		return sql;
	}

	public String insertData2(String TagId, String DeviceId, String WorkerId, String Tag, int Location, String TagTime,
			int Flag, int Count) {
		// timeCheck = java.sql.Date.valueOf(TagTime);
		String sql = null;
		if (Flag == 1) {
			// Query문 준비
			sql = "REPLACE INTO tb_device_current_count (DEVICE_ID, LOCATION, COUNT)" + "VALUES('" + DeviceId + "', '"
					+ Location + "', '" + Count + "')";
			System.out.println(sql);
		} else if (Flag == 0) {
			// Query문 준비
			sql = "REPLACE INTO tb_device_current_count (DEVICE_ID, LOCATION, COUNT)" + "VALUES('" + DeviceId + "', '"
					+ Location + "', '" + Count + "')";
			System.out.println(sql);
		}

		// System.out.println(sql);

		return sql;
	}

	public String insertData3(String TagId, String DeviceId, String WorkerId, String Tag, int Location, String TagTime,
			int Flag, int Count) {
		// timeCheck = java.sql.Date.valueOf(TagTime);
		String sql = null;
		if (Flag == 1) {
			// Query문 준비
			sql = "REPLACE INTO tb_tag_current_location (TAG, DEVICE_ID, LOCATION)" + "VALUES('" + Tag + "', '"
					+ DeviceId + "', '" + Location + "')";
			System.out.println(sql);
		} else if (Flag == 0) {
			// Query문 준비
			sql = "DELETE FROM tb_tag_current_location WHERE TAG = '" + Tag + "' AND DEVICE_ID = '" + DeviceId
					+ "' AND LOCATION = '" + Location + "'";

			System.out.println(sql);
		}

		// System.out.println(sql);

		return sql;
	}

	private String timeCheck = null;
	private String BeforeTime = null;
	String sql = null;

	public String insertData4(String TagId, String DeviceId, String WorkerId, String Tag, int Location, String TagTime,
			int Flag, int Count, int StockCount, int InputCount, int OutputCount) {
		timeCheck = TagTime.substring(0, 10);
		// DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
		String converttime = timeCheck;

		if (converttime == BeforeTime) {
			if (Flag == 1) {
				// Query문 준비
				sql = "REPLACE INTO tb_count_per_day (DEVICE_ID, STOCK_DATE ,STOCK_COUNT, INPUT_COUNT, OUTPUT_COUNT)"
						+ "VALUES('" + DeviceId + "', '" + TagTime + "', '" + StockCount + "', '" + InputCount + "', '"
						+ OutputCount + "')";
				System.out.println(sql);
			} else if (Flag == 0) {
				// Query문 준비
				sql = "REPLACE INTO tb_count_per_day (DEVICE_ID, STOCK_DATE ,STOCK_COUNT, INPUT_COUNT, OUTPUT_COUNT)"
						+ "VALUES('" + DeviceId + "', '" + TagTime + "', '" + StockCount + "', '" + InputCount + "', '"
						+ OutputCount + "')";
				System.out.println(sql);
			}
		} else if (BeforeTime != null && converttime != BeforeTime) {
			if (Flag == 1) {
				// Query문 준비
				sql = "REPLACE INTO tb_count_per_day (DEVICE_ID, STOCK_DATE ,STOCK_COUNT, INPUT_COUNT, OUTPUT_COUNT)"
						+ "VALUES('" + DeviceId + "', '" + TagTime + "', '" + StockCount + "', '" + InputCount + "', '"
						+ OutputCount + "')";
				System.out.println(sql);
			} else if (Flag == 0) {
				// Query문 준비
				sql = "REPLACE INTO tb_count_per_day (DEVICE_ID, STOCK_DATE ,STOCK_COUNT, INPUT_COUNT, OUTPUT_COUNT)"
						+ "VALUES('" + DeviceId + "', '" + TagTime + "', '" + StockCount + "', '" + InputCount + "', '"
						+ OutputCount + "')";
				System.out.println(sql);
			}
		} else if (BeforeTime == null) {
			if (Flag == 1) {
				// Query문 준비
				sql = "REPLACE INTO tb_count_per_day (DEVICE_ID, STOCK_DATE ,STOCK_COUNT, INPUT_COUNT, OUTPUT_COUNT)"
						+ "VALUES('" + DeviceId + "', '" + TagTime + "', '" + StockCount + "', '" + InputCount + "', '"
						+ OutputCount + "')";
				System.out.println(sql);
			} else if (Flag == 0) {
				// Query문 준비
				sql = "REPLACE INTO tb_count_per_day (DEVICE_ID, STOCK_DATE ,STOCK_COUNT, INPUT_COUNT, OUTPUT_COUNT)"
						+ "VALUES('" + DeviceId + "', '" + TagTime + "', '" + StockCount + "', '" + InputCount + "', '"
						+ OutputCount + "')";
				System.out.println(sql);
			}
		}

		BeforeTime = converttime;
		// System.out.println(sql);

		return sql;
	}

	public static void main(String[] args) throws IOException {

		// mariaDB 연동
		driver = "org.mariadb.jdbc.Driver";
		DB_IP = "119.196.33.217";
		DB_PORT = "3307";
		DB_NAME = "autocabinet_web";
		DB_URL = "jdbc:mariadb://" + DB_IP + ":" + DB_PORT + "/" + DB_NAME;

		conn = null;
		pstmt = null;
		rs = null;

		try {
			Class.forName(driver);
			conn = DriverManager.getConnection(DB_URL, "autoCabinet", "123456");
			if (conn != null) {
				System.out.println("DB 접속 성공");
			}
		} catch (ClassNotFoundException e) {
			System.out.println("드라이버 로드 실패");
			e.printStackTrace();
		} catch (SQLException e) {
			System.out.println("DB 접속 실패");
			e.printStackTrace();
		}

		ServerSocket serverSock = new ServerSocket(9999);
		System.out.println(serverSock + ": 서버소켓생성");

		while (true) {
			client = serverSock.accept();
			clients.add(client);

			ServerClass myServer = new ServerClass(client);
			myServer.start();
		}

	}

}
