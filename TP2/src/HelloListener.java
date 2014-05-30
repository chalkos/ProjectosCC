
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

class HelloListener extends Thread {

    SimpleDateFormat ssd = new SimpleDateFormat();
    GregorianCalendar sdd = new GregorianCalendar();

    public HelloTable tabela;

    private MulticastSocket mSocket;
    private byte[] buf = new byte[1000];
    private String id = "";

    public HelloListener(HelloTable tabela, MulticastSocket mSocket) {
        this.tabela = tabela;
        this.mSocket = mSocket;
        StringBuilder sddd = new StringBuilder();

        sdd.add(Calendar.YEAR, MIN_PRIORITY);
    }

    public HelloListener(HelloTable tabela, MulticastSocket mSocket, int id) {
        this(tabela, mSocket);
        this.id = "#" + id;
    }

    @Override
    public void run() {
        while (true) {
            try {
                //System.out.println("[listener] get datagram packet");
                DatagramPacket recv = new DatagramPacket(buf, buf.length);
                mSocket.receive(recv);

                String enviado_por = recv.getAddress().toString();//Saber quem enviou o Pacote

                //System.out.println("[listener] get stream");
                ByteArrayInputStream bais = new ByteArrayInputStream(buf);
                ObjectInputStream ois = new ObjectInputStream(bais);
                Object o = ois.readObject();

                HelloPacket pacote = null;
                RouteReplyPacket routerep = null;
                RouteRequestPacket routereq = null;

                pacote = (HelloPacket) o;
                routerep = (RouteReplyPacket) o;
                routereq = (RouteRequestPacket) o;

                if (pacote != null) {
                    //System.out.println("[Listener" + id + "] Got package!");
                    System.out.println("Recebi um HelloPacket");

                    this.tabela.novaEntrada(recv.getAddress().getHostAddress(), pacote.getVizinhos());

                    if (pacote.responder) {
                        InetAddress dest = InetAddress.getByName(recv.getAddress().getHostName());

                        DatagramSocket s = new DatagramSocket(0);

                        HelloPacket resposta = new HelloPacket(tabela.getVizinhos());
                        resposta.responder = false;

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(baos);
                        oos.writeObject(resposta);

                        //System.out.println("[Listener" + id + "] Respondeu.");
                        System.out.println("Enviei um HelloPacket");

                        byte[] aEnviar = baos.toByteArray();

                        DatagramPacket p = new DatagramPacket(aEnviar, aEnviar.length, recv.getSocketAddress());

                        s.send(p);
                        s.close();
                    }
                } else if (routereq != null) {

                    //System.out.println("[Listener" + id + "] Got package!");
                    System.out.println("Recebi um RouteRequestPacket");

                    String meuinet = InetAddress.getLocalHost().toString();

                    //Verificar se já veio por aqui
                    for (String s : routereq.getRota()) {
                        if (s.compareTo(meuinet) == 0)//Já passou por cá
                        {
                            break;//Pára tudo
                        }
                    }

                    if (routereq.getMaxSaltos() > routereq.getNsaltos()) {//Ainda não está no último

                        InetAddress dest = InetAddress.getByName(recv.getAddress().getHostName());

                        DatagramSocket s = new DatagramSocket(0);

                        RouteReplyPacket resposta = new RouteReplyPacket(routerep);

                        resposta.incNsaltos();//Incrementar o número de saltos
                        resposta.addNodo(meuinet);//Não sei se é INET ou MAC

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(baos);
                        oos.writeObject(resposta);
                        byte[] aEnviar = baos.toByteArray();

                        //System.out.println("[Listener" + id + "] Respondeu.");
                        System.out.println("Enviei um RouteReplyPacket");

                        if (routereq.getDestino().compareTo(meuinet) == 0)//O Destino sou eu
                        {
                            RouteReplyPacket reply = new RouteReplyPacket(routereq.getRota());
                            reply.addNodo(meuinet);
                            DatagramPacket p2 = new DatagramPacket(aEnviar, aEnviar.length, InetAddress.getByName(reply.getRota().get(0)), 999);

                            s.send(p2);
                            s.close();
                        } else {
                            for (String vizinho : tabela.getVizinhos()) {

                                if (routereq.getRota().contains(vizinho) == false)//Se o vizinho não está na lista de rota do pacote recebido
                                {
                                    DatagramPacket p2 = new DatagramPacket(aEnviar, aEnviar.length, InetAddress.getByName(vizinho), 999);

                                    s.send(p2);
                                    s.close();
                                }
                            }
                        }
                    }

                }
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(HelloListener.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
