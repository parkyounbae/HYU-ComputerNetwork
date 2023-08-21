import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.security.MessageDigest;
import java.util.Scanner;

public class UDPClient {
    // 사용자가 입력한 이름을 저장
    static String name;
    // 아이피 주소를 저장
    static InetAddress addr;
    // 포트번호를 저장
    static int port;
    // 멀티캐스트 객체 생성
    static MulticastSocket group;

    public static void main(String[] args) {
        // 시스템을 시작할때 입력값(포트번호)를 받는다
       if(args.length != 1) {
           // 포트번호를 입력받지 못했다면 프로그램 종료
           System.out.println("Wrong! Please set Port... exit Program");
           System.exit(0);
       } else {
           // 입력받은 값을 포트 번호로 지정
           port = Integer.parseInt(args[0]);
       }
       // 스캐너 초기화
       Scanner sc = new Scanner(System.in);
       while (true) {
           // 채팅 프로그램 실행 후 명령어 기다림
           System.out.println("insert #JOIN <room> <name>");
           String allMessage = sc.nextLine();
           String[] splittedString = allMessage.split(" ", 3);

           // #JOIN 이라면
           if(splittedString[0].equals("#JOIN")) {
               try {
                   // sha-256 해시 알고리즘을 사용하기 위해 MessageDigest 생성
                   MessageDigest md = MessageDigest.getInstance("SHA-256");
                   // update 를 통해 채팅방 이름을 이용한 해시 알고리즘 값을 구한다
                   md.update(splittedString[1].getBytes());
                   // 바이트 배열에 저장
                   byte[] bytes = md.digest();
                   StringBuilder builder = new StringBuilder();
                   // 아이피의 앞부분 225 를 먼저 넣기
                   builder.append("225");
                   for (int i=bytes.length-3 ; i<bytes.length ; i++) {
                       // and 연산을 통해 주어진 값을 255이하의 값으로 설정한 뒤 문자열에 추가한다.
                       builder.append(String.format(".%d", bytes[i]&0xff));
                   }
                   // 얻은 아이피 주소 저장
                   addr = InetAddress.getByName(builder.toString());
                   // 입력받은 이름 저장
                   name = splittedString[2];
                   // 포트 번호를 이용해 멀티캐스트 소켓 설정
                   group = new MulticastSocket(port);
                   // 수신자, 송신자를 생성하고 쓰레드 동작
                   Receiver rThread = new Receiver();
                   Sender sThread = new Sender();
                   rThread.start();
                   sThread.start();

                   // 위의 쓰레드가 종료되기 전까지 stop
                   sThread.join();
                   rThread.join();
               } catch (Exception e) {
                   e.printStackTrace();
               }
               System.out.println("You left chat room.");
           }
       }
    }

    // 송신자
    static class Sender extends Thread {
        public void run() {
            try {
                // 유저의 입력값 받는 버퍼 생성
                BufferedReader fromUser = new BufferedReader(new InputStreamReader(System.in));
                // 켜져있음
                boolean isTurnOn = true;
                while (isTurnOn) {
                    // 한줄씩 읽은 값 저장
                    String userTyped = fromUser.readLine();
                    // 입력한 값이 #EXIT 라면?
                    if(userTyped.equals("#EXIT")) {
                        // 나갔다는 메시지 전송
                        String msg = name + " has been left.";
                        // 메시지를 바이트로 전환
                        byte[] out = msg.getBytes();
                        // 패킷으로 싸서
                        DatagramPacket pkt = new DatagramPacket(out, out.length, addr, port);
                        // 그룹에게 전송
                        group.send(pkt);
                        // 나간다는 명령이니까 false
                        isTurnOn = false;
                    } else {
                        // 메시지에 보낸 사람의 이름을 붙임
                        String msg = name + ":" + userTyped;
                        // 바이트로 변환 후 전송
                        byte[] out = msg.getBytes();
                        DatagramPacket pkt = new DatagramPacket(out, out.length, addr, port);
                        group.send(pkt);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class Receiver extends Thread {
        public void run() {
            try {
                // 512 바이트 씩 입력받음
                byte[] in = new byte[512];
                // 해당하는 길이의 패킷을 받을 객체 생성
                DatagramPacket pkt = new DatagramPacket(in, in.length);
                // 아이피 주소에 대한 그룹 조인
                group.joinGroup(addr);
                boolean isTurnOn = true;
                while (isTurnOn) {
                    // 패킷을 계속해서 받음
                    group.receive(pkt);
                    // 받은 패킷을 String 으로 변환
                    String receivedPkt = new String(pkt.getData(), 0, pkt.getLength());
                    // 받은 string 값이 "내 이름 + 나갔습니다" 라면
                    if(receivedPkt.equals(name + " has been left.")) {
                        // 꺼지고 그룹을 떠남
                        isTurnOn = false;
                        group.leaveGroup(addr);
                    } else {
                        System.out.println(receivedPkt);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
