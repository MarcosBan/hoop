package dcm

import (
	"fmt"

	pb "github.com/runopsio/hoop/common/proto"
	pbagent "github.com/runopsio/hoop/common/proto/agent"
	"github.com/runopsio/hoop/gateway/plugin"
	plugintypes "github.com/runopsio/hoop/gateway/transport/plugins/types"
	"github.com/runopsio/hoop/gateway/user"
)

type dcm struct {
	pluginSvc *plugin.Service
}

func New(pluginSvc *plugin.Service) *dcm             { return &dcm{pluginSvc: pluginSvc} }
func (p *dcm) Name() string                          { return plugintypes.PluginDatabaseCredentialsManagerName }
func (p *dcm) OnStartup(_ plugintypes.Context) error { return nil }
func (p *dcm) OnConnect(_ plugintypes.Context) error { return nil }
func (p *dcm) OnReceive(pctx plugintypes.Context, pkt *pb.Packet) (*plugintypes.ConnectResponse, error) {
	if pkt.Type != pbagent.SessionOpen {
		return nil, nil
	}
	pl, err := p.pluginSvc.FindOne(user.NewContext(pctx.OrgID, pctx.UserID), p.Name())
	if err != nil {
		return nil, plugintypes.InternalErr("failed fetching database credentials manager plugin", err)
	}
	policy, err := parsePolicyConfig(pctx.ConnectionName, pl)
	if err != nil {
		return nil, fmt.Errorf("failed parsing policy configuration, reason=%v", err)
	}
	if policy.Expiration == "" {
		policy.Expiration = defaultExpirationDuration.String()
	}
	checksum, err := newPolicyChecksum(policy)
	if err != nil {
		return nil, err
	}
	encDcmData, err := pb.GobEncode(map[string]any{
		"name":             policy.Name,
		"engine":           policy.Engine,
		"datasource":       policy.datasource,
		"instances":        policy.Instances,
		"grant-privileges": policy.GrantPrivileges,
		"expiration":       policy.Expiration,
		"checksum":         checksum,
	})
	if err != nil {
		return nil, plugintypes.InternalErr("failed encoding plugin data", err)
	}
	pkt.Spec[pb.SpecPluginDcmDataKey] = encDcmData
	return nil, nil
}
func (p *dcm) OnDisconnect(_ plugintypes.Context, _ error) error { return nil }
func (p *dcm) OnShutdown()                                       {}