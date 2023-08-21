import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        // args[0] : server ip, args[1] : port for chat with Client , args[2] : port for #GET, #PUT
        if (args.length != 3) {
            // 입력값 충족시키지 못했으면 프로그램 종
            System.out.println("There is wrong input.");
            System.exit(-1);
        }

        // 채팅에 대한 소켓
        Socket socket = null;
        Socket fileSocket = null;

        // 파일에 대한 소켓
        PrintWriter writer = null;
        BufferedReader reader = null;

        // 클라이언트 하나당 폴더 제작
        String filePath = "../Files";   // System.getProperty("user.dir") +"../Files";

        while (true) {
            System.out.println("채팅방 생성 : #CREATE (채팅방 이름) (사용자 이름)");
            System.out.println("채팅방 입장 : #JOIN (채팅방 이름) (사용자 이름)");
            // 명령어 입력받고 공백기준으로 저장
            Scanner scanner = new Scanner(System.in);
            String fullCommand = scanner.nextLine();
            String[] commandList = fullCommand.split(" ");

            try {
                // 입력받았던 서버아이파와 포트번호로 채팅과 파일에 대한 소켓과 버퍼리더 스트림 생성
                BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
                socket = new Socket(args[0], Integer.parseInt(args[1])); // 서버 아이피와 포트번호로 접속
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                fileSocket = new Socket(args[0], Integer.parseInt(args[2]));
                DataInputStream dataInputStream = new DataInputStream(fileSocket.getInputStream());
                DataOutputStream dataOutputStream = new DataOutputStream(fileSocket.getOutputStream());

                if(commandList[0].equals("#CREATE") || commandList[0].equals("#JOIN")) {
                    // 입력받은 명령어가 생성일 경우
                    // 생성 명령어 서버로 전송
                    writer.println(fullCommand);
                    writer.flush();

                    // 서버에서 들려오는 채팅을 읽기 위해 쓰레드 생성
                    PrintThread thread = new PrintThread(socket, reader);
                    thread.start();

                    // 서버에서 오는 파일을 읽기 위해 쓰레드 생성
                    FilePrintThread filePrintThread = new FilePrintThread(fileSocket,commandList[2]);
                    filePrintThread.start();

                    // 받은 파일을 저장할 폴더 생성
                    String pathWithName = filePath + "/" +commandList[2];

                    File folder = new File(pathWithName);

                    if(!folder.exists()) {
                        try {
                            folder.mkdir();
                        } catch (Exception e) {
                            e.getStackTrace();
                        }
                    }

                    String msg;
                    while (true) {
                        // 소켓이 열려 있는 한 계속 반복
                        if(socket.isClosed()) {
                            writer.close();
                            break;
                        }

                        // 유저 입력 받기
                        msg = input.readLine();

                        // 각각 명령어들이 있고 명령어가 입력되었을 경우 각각의 일을 수행한다.
                        // 명령어가 아닌 경우에는 서버에 채팅 내용을 전달한다.
                        if(msg.equals("#EXIT")) {
                            // 입력받은 값의 처음이 exit일 경우
                            // exit + 채팅방 이름 + 사용자 이름 을 서버로 전달
                            msg = msg + " " + commandList[1] + " " + commandList[2];
                            writer.println(msg);
                            writer.flush();

                            // 소켓, writer, reader 닫기
                            try {
                                if (writer != null) writer.close();
                                if (reader != null) reader.close();
                                if (socket != null) socket.close();
                                if (fileSocket != null) fileSocket.close();
                                break;
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else if(msg.split(" ")[0].equals("#GET")) {
                            // 겟 + 파일이름 서버에 전달
                            writer.println(msg);
                            writer.flush();
                        } else if(msg.split(" ")[0].equals("#PUT")) {
                            // 서버에 풋 명령어 전달
                            writer.println("#PUT");
                            writer.flush();
                            // 전달받은 파일 이름 을 통해 해당 파일 불러오기
                            String fileToPut = msg.split(" ")[1];
                            String filePathFinal = pathWithName + "/" + fileToPut;
                            System.out.println(filePathFinal); // todo
                            File file = new File(filePathFinal);
                            if(file.isFile()) {
                                // 해당 파일이 존재한다면 해당 파일의 이름과 파일의 길이를 서버에 전달
                                dataOutputStream.writeUTF(file.getName());
                                FileInputStream fileInputStream = new FileInputStream(file);
                                dataOutputStream.writeLong(file.length());

                                int length;
                                byte[] buffer = new byte[1024];

                                // 파일을 쪼개서 서버로 전송 시작
                                StringBuilder fileIndex = new StringBuilder();
                                fileIndex.append("파일 전송 시작 : ");
                                while ((length = fileInputStream.read(buffer))!=-1) {
                                    dataOutputStream.write(buffer,0,length);
                                    dataOutputStream.flush();
                                    fileIndex.append("#");
                                    System.out.println(fileIndex.toString());
                                }
                                System.out.println("파일 전송 완료!");
                            } else {
                                System.out.println("해당 파일이 존재하지 않습니다.");
                            }

                        } else if(msg.split(" ")[0].equals("#STATUS")) {
                            msg = "#STATUS";
                            writer.println(msg);
                            writer.flush();
                        } else {
                            msg = "[@" + commandList[1] + "] " + commandList[2] + " : " + msg;
                            writer.println(msg);
                            writer.flush();
                        }
                    }
                } else {
                    System.out.println("WRONG COMMAND");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
    }
}

// 파일 송수신을 담당하는 쓰레드
class FilePrintThread extends Thread {
    private final Socket socket; // 소켓
    private final DataInputStream dataInputStream;
    private final String filePath; // 파일 경로

    public FilePrintThread(Socket socket, String userName) throws IOException {
        this.socket = socket;
        this.dataInputStream = new DataInputStream(socket.getInputStream());
        this.filePath = "../Files/"+userName;
    }

    @Override
    public void run() {
        // 파일을 받기 위해 항시적으로 쓰레드를 돌림
        try {
            while (true) {
                if(this.socket.isClosed()) {
                    // System.out.println("file socket closed");
                    break;
                }
                if (dataInputStream.available() != 0) {
                    // 반응이 오면 해당하는 파일 새로 생성후 쪼개져서 오는 파일을 합치면서 받아옴
                    String fileNameToPut = dataInputStream.readUTF();
                    File file = new File(filePath+"/"+fileNameToPut); // 여기에 경로 + 파일명
                    file.createNewFile();
                    System.out.println("파일 수신이 시작됩니다.");
                    long fileSize = dataInputStream.readLong();
                    long data = 0;
                    int length;
                    byte[] buffer = new byte[1024];

                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("진행도 : #");
                    while((length = dataInputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer,0,length);
                        data += length;

                        System.out.println(stringBuilder.toString());
                        stringBuilder.append("#");

                        if(data == fileSize) break;
                    }
                    System.out.println("파일 수신 완료!");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (this.socket != null) this.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

// 채팅을 위한 쓰레드
class PrintThread extends Thread {
    private final Socket socket;
    private final BufferedReader reader;

    public PrintThread(Socket socket, BufferedReader reader) {
        this.socket = socket;
        this.reader = reader;
    }

    // 서버에서 오는 채팅을 받기 위해 항상 돌아감
    @Override
    public void run() {
        try {
            String line;
            while((line = this.reader.readLine()) != null) {
                String[] lineList = line.split(" ",3);
                // 만약에 fail 이 오면 종료
                if(lineList[0].equals("#FAIL")) {
                    System.out.println(line);
                    break;
                } else  {
                    System.out.println(line);
                }
            }
        } catch (IOException e) {

        } finally {
            try {
                if (this.reader != null) this.reader.close();
                if (this.socket != null) this.socket.close();
            } catch (IOException e) {

            }
        }
    }
}

