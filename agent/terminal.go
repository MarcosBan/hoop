package agent

import (
	"fmt"
	"log"
	"strconv"
	"syscall"

	"github.com/runopsio/hoop/agent/dlp"
	term "github.com/runopsio/hoop/agent/terminal"
	pb "github.com/runopsio/hoop/common/proto"
	"github.com/runopsio/hoop/common/runtime"
)

func (a *Agent) doTerminalWriteAgentStdin(pkt *pb.Packet) {
	sessionID := string(pkt.Spec[pb.SpecGatewaySessionID])
	sessionIDKey := fmt.Sprintf(cmdStoreKey, sessionID)
	cmdObj := a.connStore.Get(sessionIDKey)
	cmd, ok := cmdObj.(*term.Command)
	if ok {
		// Write to tty stdin content
		if err := cmd.WriteTTY(pkt.Payload); err != nil {
			log.Printf("session=%v | tty=true - failed copying stdin to tty, err=%v", string(sessionID), err)
			a.sendCloseTerm(sessionID, "", "")
		}
		return
	}

	connParamsObj := a.connStore.Get(fmt.Sprintf(connectionStoreParamsKey, string(sessionID)))
	connParams, ok := connParamsObj.(*pb.AgentConnectionParams)
	if !ok {
		log.Printf("session=%s - connection params not found", sessionID)
		a.sendCloseTerm(sessionID, "internal error, connection params not found", "")
		return
	}

	cmd, err := term.NewCommand(connParams.EnvVars, append(connParams.CmdList, connParams.ClientArgs...)...)
	if err != nil {
		log.Printf("session=%s, tty=true - failed executing command, err=%v", sessionID, err)
		a.sendCloseTerm(sessionID, "failed executing command", "")
		return
	}
	log.Printf("session=%s, tty=true - executing command %q", sessionID, cmd.String())
	spec := map[string][]byte{pb.SpecGatewaySessionID: []byte(sessionID)}
	onExecErr := func(exitCode int, errMsg string, v ...any) {
		a.sendCloseTerm(sessionID, fmt.Sprintf(errMsg, v...), strconv.Itoa(exitCode))
	}
	stdoutWriter := pb.NewStreamWriter(a.client, pb.PacketTerminalClientWriteStdoutType, spec)
	if dlpClient, ok := a.connStore.Get(dlpClientKey).(dlp.Client); ok {
		stdoutWriter = dlp.NewDLPStreamWriter(
			a.client,
			dlpClient,
			pb.PacketTerminalClientWriteStdoutType,
			spec,
			connParams.DLPInfoTypes)
	}
	if err := cmd.RunOnTTY(stdoutWriter, onExecErr); err != nil {
		log.Printf("session=%s, tty=true - err=%v", string(sessionID), err)
	}
	a.connStore.Set(sessionIDKey, cmd)
}

func (a *Agent) doTerminalCloseTerm(pkt *pb.Packet) {
	sessionID := pkt.Spec[pb.SpecGatewaySessionID]
	log.Printf("session=%v - received %v", string(sessionID), pb.PacketTerminalCloseType)
	procPidObj := a.connStore.Get(fmt.Sprintf("proc:%s", sessionID))
	if procPid, _ := procPidObj.(int); procPid > 0 {
		log.Printf("sending SIGINT signal to process %v ...", procPid)
		go runtime.Kill(procPid, syscall.SIGINT)
	}
}

func (a *Agent) sendCloseTerm(sessionID, msg string, exitCode string) {
	spec := map[string][]byte{pb.SpecGatewaySessionID: []byte(sessionID)}
	if exitCode != "" {
		spec[pb.SpecClientExecExitCodeKey] = []byte(exitCode)
	}
	_, _ = pb.NewStreamWriter(a.client, pb.PacketTerminalCloseType, spec).Write([]byte(msg))
}