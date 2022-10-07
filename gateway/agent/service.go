package agent

import "github.com/runopsio/hoop/gateway/user"

type (
	Service struct {
		Storage storage
	}

	storage interface {
		Persist(agent *Agent) (int64, error)
		FindAll(context *user.Context) ([]Agent, error)
		FindById(id string) (*Agent, error)
		FindByToken(token string) (*Agent, error)
	}

	Agent struct {
		Id            string `json:"id"             edn:"xt/id"`
		Token         string `json:"token"          edn:"agent/token"`
		OrgId         string `json:"-"              edn:"agent/org"`
		Name          string `json:"name"           edn:"agent/name"`
		Hostname      string `json:"hostname"       edn:"agent/hostname"`
		MachineId     string `json:"machine-id"     edn:"agent/machine-id"`
		KernelVersion string `json:"kernel_version" edn:"agent/kernel-version"`
		CreatedById   string `json:"-"              edn:"agent/created-by"`
		Status        Status `json:"status"         edn:"agent/status"`
	}

	Status string
)

const (
	StatusConnected    Status = "CONNECTED"
	StatusDisconnected Status = "DISCONNECTED"
)

func (s *Service) FindById(id string) (*Agent, error) {
	return s.Storage.FindById(id)
}

func (s *Service) FindByToken(token string) (*Agent, error) {
	return s.Storage.FindByToken(token)
}

func (s *Service) Persist(agent *Agent) (int64, error) {
	return s.Storage.Persist(agent)
}

func (s *Service) FindAll(context *user.Context) ([]Agent, error) {
	return s.Storage.FindAll(context)
}