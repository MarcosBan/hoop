package proxypg

import (
	"fmt"
	"io"
	"log"
	"net"
	"strconv"

	"github.com/runopsio/hoop/common/memory"
	pb "github.com/runopsio/hoop/common/proto"
)

const defaultPostgresPort = "5433"

type Server struct {
	listenPort      string
	client          pb.ClientTransport
	connectionStore memory.Store
	listener        net.Listener
}

func New(listenPort string, client pb.ClientTransport) *Server {
	if listenPort == "" {
		listenPort = defaultPostgresPort
	}
	return &Server{
		listenPort:      listenPort,
		client:          client,
		connectionStore: memory.New(),
	}
}

func (p *Server) Serve(sessionID string) error {
	listenAddr := fmt.Sprintf("127.0.0.1:%s", p.listenPort)
	lis, err := net.Listen("tcp4", listenAddr)
	if err != nil {
		return fmt.Errorf("failed listening to address %v, err=%v", listenAddr, err)
	}
	p.listener = lis
	go func() {
		connectionID := 0
		for {
			connectionID++
			pgClient, err := lis.Accept()
			if err != nil {
				log.Printf("failed obtain listening connection, err=%v", err)
				break
			}
			go p.serveConn(sessionID, strconv.Itoa(connectionID), pgClient)
		}
	}()
	return nil
}

func (p *Server) serveConn(sessionID, connectionID string, pgClient net.Conn) {
	defer func() {
		log.Printf("session=%v | conn=%s | remote=%s - closing tcp connection",
			sessionID, connectionID, pgClient.RemoteAddr())
		p.connectionStore.Del(connectionID)
		if err := pgClient.Close(); err != nil {
			// TODO: log warn
			log.Printf("failed closing client connection, err=%v", err)
		}
		_ = p.client.Send(&pb.Packet{
			Type: pb.PacketCloseConnectionType.String(),
			Spec: map[string][]byte{
				pb.SpecClientConnectionID: []byte(connectionID),
				pb.SpecGatewaySessionID:   []byte(sessionID),
			}})
	}()
	connWrapper := pb.NewConnectionWrapper(pgClient, make(chan struct{}))
	p.connectionStore.Set(connectionID, connWrapper)

	log.Printf("session=%v | conn=%s | client=%s - connected", sessionID, connectionID, pgClient.RemoteAddr())
	pgServerWriter := pb.NewStreamWriter(p.client, pb.PacketPGWriteServerType, map[string][]byte{
		string(pb.SpecClientConnectionID): []byte(connectionID),
		string(pb.SpecGatewaySessionID):   []byte(sessionID),
	})
	if _, err := io.CopyBuffer(pgServerWriter, pgClient, nil); err != nil {
		log.Printf("failed copying buffer, err=%v", err)
		connWrapper.Close()
	}
}

func (p *Server) PacketWriteClient(connectionID string, pkt *pb.Packet) (int, error) {
	conn, err := p.getConnection(connectionID)
	if err != nil {
		return 0, err
	}
	return conn.Write(pkt.Payload)
}

func (p *Server) PacketCloseConnection(connectionID string) {
	if conn, err := p.getConnection(connectionID); err == nil {
		_ = conn.Close()
	}
	_ = p.listener.Close()
}

func (p *Server) getConnection(connectionID string) (io.WriteCloser, error) {
	connWrapperObj := p.connectionStore.Get(connectionID)
	conn, ok := connWrapperObj.(io.WriteCloser)
	if !ok {
		return nil, fmt.Errorf("local connection %q not found", connectionID)
	}
	return conn, nil
}

func (p *Server) ListenPort() string {
	return p.listenPort
}