import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ChatServer {
    public static void main(String[] args) {
        // args[0] : port for chat with Client , args[1] : port for #GET, #PUT
        if(args.length != 2) {
            // 충족하지 못하면 프로그램 종료
            System.out.println("There is wrong input.");
            System.exit(-1);
        }
        // 와일문에서 지속적으로 클라이언트의 요청을 대기한다.
        while (true) {
            try {
                ServerSocket server = new ServerSocket(Integer.parseInt(args[0])); // 서버소켓 -> 클라이언트로부터 연결요청을 기다렸다가 오면 연결해주고 다른 소켓을 만든다.
                ServerSocket fileServer = new ServerSocket(Integer.parseInt(args[1])); // 채팅서버와 마찬가지로 파일서버도 준비한다.
                while (true) {
                    System.out.println("- 클라이언트의 요청 대기중 -");
                    Socket socket = server.accept(); // 클라이언트가 연결 요철할 때 까지 대 // 여기서 blocking
                    if(!socket.isClosed()) {
                        System.out.println("파일서버 연결 준비.."); // 채팅서버 연결이 성공적으로 이루어졌다면 파일서버도 연결한다.
                        Socket fileSocket = fileServer.accept();
                        FileThread fileThread = new FileThread(fileSocket);
                        fileThread.start(); // 연결이 성공적으로 이루어졌다면 파일을 받기를 기다리는 쓰레드와 채팅을 받기를 기다리는 쓰레드를 시작한다.
                        System.out.println("파일서버 연결 완료!");
                        ChatThread thread = new ChatThread(socket,fileThread);
                        thread.start();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

class FileThread extends Thread {
    private final Socket socket; // 소켓
    //private final BufferedReader reader;
    private final DataInputStream dataInputStream; // 들어오는 데이터를 받아들이는
    private final DataOutputStream dataOutputStream; // 데이터를 내보내는
    private final String filePath; // 파일을 저장할 경로

    private String roomName; // 현재 접속된 방 이름 정보
    private String id; // 현재 접속된 사람

    public static final HashMap<String, String> idWithRoom = new HashMap<>(); // (사람이름 + 방 이름)이 저장되는 맵
    public static final HashMap<String, String> fileWithRoom = new HashMap<>(); // (파일이름 + 방 이름)이 저장되는 맵

    // 생성된 객체의 아이디와 방 이름을 설정해주는 함수
    public void setIdAndRoom(String id,String roomName) {
        this.id = id;
        this.roomName = roomName;

        synchronized (idWithRoom) {
            idWithRoom.put(id,roomName);
        }
    }

    // 객체를 생성할때 전달받은 소켓을 이용해 스트림을 뚫어둔다.
    public FileThread(Socket socket) throws IOException {
        this.socket = socket;
        this.dataInputStream = new DataInputStream(socket.getInputStream());
        this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
        this.filePath = "../Files/ServerFiles";
    }

    // 파일을 보내는 함수
    public int sendFile(String fileToPut) {
        // 보낼 파일의 경로아 이름을 통해 불러온다
        File file = new File(filePath + "/" + fileToPut);
        try {
            // 해당파일이 존재하고 그 파일이 해당 채팅방에 속할경우 보낸다.
            if(file.isFile() && fileWithRoom.get(fileToPut).equals(roomName)) {
                System.out.println(fileToPut + " 파일을 전송하겠습니다. "); // todo
                // 상대방에게 파일 이름, 파일의 길이를 보내줌
                dataOutputStream.writeUTF(file.getName());
                FileInputStream fileInputStream = new FileInputStream(file);
                dataOutputStream.writeLong(file.length());

                int length;
                byte[] buffer = new byte[1024];

                System.out.println(" 파일을 전송시작. ");
                while ((length = fileInputStream.read(buffer))!=-1) {
                    dataOutputStream.write(buffer,0,length);
                    dataOutputStream.flush();
                }
                System.out.println("파일 전송 완료");
                return 1;
            } else {
                System.out.println("해당 파일이 존재하지 않습니다.");
                return 0;
            }
        } catch (Exception e) {
            e.getStackTrace();
        }
        return 0;
    }

    // 파일을 받는 부분은 지속적으로 요청을 기다려야 하기 때문에 쓰레드로 돌린다.
    @Override
    public void run() {
        try {
            while (true) {
                if(this.socket.isClosed()) {
                    // System.out.println("file socket closed");
                    break;
                }
                // datainputstream 에서 반응이 있다면?
                if (dataInputStream.available() != 0) {
                    // 사용자가 보냄 파일 이름 전달 받고
                    String fileNameToPut = dataInputStream.readUTF();
                    // 경로와 파일명을 통해 해당 파일을 새로 생성한다.
                    File file = new File(filePath+"/"+fileNameToPut); // 여기에 경로 + 파일명
                    file.createNewFile();
                    System.out.println(fileNameToPut + " 파일 수신 시작. ");
                    // 사용자가 보낸 파일 사이즈 전달받는다.
                    long fileSize = dataInputStream.readLong();
                    long data = 0;
                    int length;
                    byte[] buffer = new byte[1024];

                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    while((length = dataInputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer,0,length);
                        data += length;
                        if(data == fileSize) break;
                    }
                    System.out.println(fileNameToPut + " 파일 수신 완료. ");
                    synchronized (fileWithRoom) {
                        fileWithRoom.put(fileNameToPut,roomName);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


// 채팅 쓰레드
class ChatThread extends Thread {
    private final Socket socket; // 채팅 소켓
    private final String id; // 사용자 이름
    private final String roomName; // 방 이름
    private final BufferedReader reader; // 버퍼 리더
    private final FileThread fileThread; // 파일쓰레드 저장

    public static final HashMap<String, PrintWriter> socketMap = new HashMap<>(); // (이름, 소켓)을 저장하는 맵
    public static final HashMap<String, String> idWithRoom = new HashMap<>(); // (이름, 방 이름)을 저장하는 맵

    public ChatThread(Socket socket, FileThread fileThread) throws IOException {
        this.socket = socket; // 전달받은 소켓 저장
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream())); // 전달받은 소켓으로 버퍼리더 생성
        this.fileThread = fileThread; // 전달받은 파일쓰레드 저장

        // 명령어 입력받음
        // command + roomname + id
        String command = reader.readLine();
        String[] commandList = command.split(" ");

        this.id = commandList[2];
        this.roomName = commandList[1];

        synchronized (socketMap) {
            // 소켓 맵에 전달받은 아이디와 소켓 저장
            socketMap.put(this.id, new PrintWriter(new OutputStreamWriter(socket.getOutputStream())));
        }

        // 전달받은 커맨드가 create 일때
        if(commandList[0].equals("#CREATE")) {
            // idwithroom 동기화
            synchronized (idWithRoom) {
                // 생성하려고하는 채팅방 이름이 겹치는지 확인
                boolean isDup = false;
                for(Map.Entry<String, String> temp : idWithRoom.entrySet()) {
                    if(temp.getValue().equals(commandList[1])) {
                        isDup = true;
                        break;
                    }
                }

                if(isDup) {
                    // 이미 존재한다면 소켓 닫기
                    // broadcastOne("#FAIL " + commandList[2] + " ALREADYEXIST", commandList[2]);
                    broadcastOne("#FAIL " + commandList[1] + " 은 이미 존재하는 채팅방입니다. ", commandList[2]);
                    this.socket.close();
                } else {
                    // 생성에 성공한다면 해시맵에 해당하는 이름+채팅방 넣고 파일쓰레드에 이름, 방 이름 전달
                    broadcastOne("@" + commandList[1] + " has been created.", commandList[2]);
                    idWithRoom.put(commandList[2], commandList[1]);
                    fileThread.setIdAndRoom(id,roomName);
                }
            }
        } else if (commandList[0].equals("#JOIN")) {
            // 참여를 한다면
            synchronized (idWithRoom) {
                // 해당 채팅방이 존재하는지 확인
                boolean isDup = false;
                for(Map.Entry<String, String> temp : idWithRoom.entrySet()) {
                    if(temp.getValue().equals(commandList[1])) {
                        isDup = true;
                        break;
                    }
                }

                if(isDup) {
                    // 해당 채팅방이 존재한다면 이름+방이름 해시맵에 저장, 파일쓰레드에 정보 설정
                    broadcastOne("@" + commandList[1] + " " + commandList[2] + " entered.", commandList[2]);
                    idWithRoom.put(commandList[2], commandList[1]);
                    fileThread.setIdAndRoom(id,roomName);
                } else {
                    // 해당 채팅방이 존재하지 않는다면 소켓 닫기.
                    broadcastOne("#FAIL " + commandList[1] + " 에 해당하는 채팅방이 존재하지 않습니다.", commandList[2]);
                    this.socket.close();
                }
            }

        } else {
            broadcastOne("#FAIL " + commandList[2] + " WRONGCOMMAND", commandList[2]);
            this.socket.close();
        }
    }

    public String getMember() {
        // 해당 채팅방에 존재하는 사용자의 이름을 스트링 값으로 전달하는 함수이다.
        StringBuilder memberList = new StringBuilder();
        memberList.append("방 이름 : " + roomName + " , 사용자 : ");
        synchronized (idWithRoom) {
            for(Map.Entry<String, String> temp : idWithRoom.entrySet()) {
                // 반복문을 돌며 현재 채팅방에 있는 사용자의 이름 추가
                if(temp.getValue().equals(roomName)) {
                    memberList.append(temp.getKey()).append(" ");
                }
            }
        }
        return memberList.toString();
    }

    public String getUserId() {
        return this.id;
    }

    public String getRoomName() {
        return this.roomName;
    }

    @Override
    public void run() {
        try {
            String line;
            // 지속적으로 전달 받기
            while ((line = this.reader.readLine()) != null) {

                // 전달받은 채팅의 커맨드에 따라 동작이 달라짐
                if(line.split(" ")[0].equals("#STATUS")) {
                    // status 일 경우에는 해당 채팅방의 멤버 목록을 전달
                    line = getMember();
                    broadcastOne(line,this.id);
                } else if (line.split(" ")[0].equals("#GET")) {
                    // 겟이면 sendFile 함수 호출
                    int result = fileThread.sendFile(line.split(" ")[1]);
                    if(result == 0) {
                        broadcastOne("해당 파일이 존재하지 않습니다. ", this.id);
                    }
                } else if(line.split(" ")[0].equals("#PUT")) {
                    System.out.println("put 요청");
                } else {
                    broadcast(line,roomName);
                }
            }
        } catch (IOException e) {

        } finally {
            synchronized (socketMap) {
                socketMap.remove(this.id);
            }
            synchronized (idWithRoom) {
                idWithRoom.remove(this.id);
            }
            // broadcast("[@"+this.roomName + "] " +this.id + " quit this chat.",roomName);
            try {
                if (this.socket != null) {
                    this.socket.close();
                }
            } catch (IOException e2) {

            }
        }
    }

    public void broadcastOne(String message, String name) {
        // 해당하는 사람에게만 방송
        System.out.println(message);
        synchronized (socketMap) {
            PrintWriter writer = socketMap.get(name);
            writer.println(message);
            writer.flush();
        }
    }

    public void broadcast(String message, String roomName) {
        // 해당하는 채팅방 전체에게 방송
        System.out.println(message);

        synchronized (idWithRoom) {
            // for 문을 돌면서 해당하는 방 이름을 가지고 있는 아이디에게 모두 방송함
           for(Map.Entry<String, String> temp : idWithRoom.entrySet()) {
                if(temp.getValue().equals(roomName) && !temp.getKey().equals(this.id)) {
                    synchronized (socket) {
                        PrintWriter writer = socketMap.get(temp.getKey());
                        writer.println(message);
                        writer.flush();
                    }
                }
            }
        }
    }
}