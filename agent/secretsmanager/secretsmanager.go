package secretsmanager

import (
	"encoding/base64"
	"fmt"
	"strings"
)

type secretsGetter interface {
	GetKey(secretID, secretKey string) (string, error)
}

type secretProviderType string

const (
	// fetch secrets from aws secrets manager
	secretProviderAWSSecretsManager secretProviderType = "_aws"
	// fetches secrets from environment variables mapped as json in unix environments
	secretProviderEnvJSON secretProviderType = "_envjson"
)

// Decode environment variables based on the provider of a certain env.
// When a value contains a _<provider>:<secret-id>:<secret-key> it will load
// the value from an external source. If the provider isn't implemented then
// it will be a noop.
func Decode(envVars map[string]any) (map[string]any, error) {
	providerSingleton := map[secretProviderType]secretsGetter{
		secretProviderAWSSecretsManager: nil,
		secretProviderEnvJSON:           nil,
	}
	decodedEnvVars := map[string]any{}
	var errors []string
	for envKey, encEnvVal := range envVars {
		attr, err := decodeVal(encEnvVal)
		if err != nil {
			errors = append(errors, fmt.Sprintf("%s %v", envKey, err))
			continue
		}
		if attr == nil {
			// it's not an secrets manager env definition
			decodedEnvVars[envKey] = encEnvVal
			continue
		}
		var provider secretsGetter
		switch attr.provider {
		case secretProviderAWSSecretsManager:
			provider = providerSingleton[secretProviderAWSSecretsManager]
			if provider == nil {
				awsProv, err := newAwsProvider()
				if err != nil {
					return nil, fmt.Errorf("failed initializing aws provider, err=%v", err)
				}
				providerSingleton[secretProviderAWSSecretsManager] = awsProv
				provider = awsProv
			}
		case secretProviderEnvJSON:
			provider = providerSingleton[secretProviderEnvJSON]
			if provider == nil {
				envJsonProv := &envJsonProvider{}
				providerSingleton[secretProviderEnvJSON] = envJsonProv
				provider = envJsonProv
			}
		default:
			// it's not an secrets manager env definition
			decodedEnvVars[envKey] = encEnvVal
			continue
		}
		val, err := provider.GetKey(attr.secretID, attr.secretKey)
		if err != nil {
			errors = append(errors, fmt.Sprintf("%s %v", envKey, err))
			continue
		}
		decodedEnvVars[envKey] = base64.StdEncoding.EncodeToString([]byte(val))
	}
	if len(errors) > 0 {
		return nil, fmt.Errorf("%q", errors)
	}
	return decodedEnvVars, nil
}

type envValAttribute struct {
	provider  secretProviderType
	secretID  string
	secretKey string
}

func decodeVal(encEnvVal any) (*envValAttribute, error) {
	v, err := base64.StdEncoding.DecodeString(fmt.Sprintf("%v", encEnvVal))
	if err != nil {
		return nil, fmt.Errorf("failed decoding value, %v", err)
	}
	parts := strings.Split(string(v), ":")
	if len(parts) != 3 {
		// it's not an secrets manager env definition
		return nil, nil
	}
	secretProvider, secretID, secretKey := secretProviderType(parts[0]), parts[1], parts[2]
	return &envValAttribute{secretProvider, secretID, secretKey}, nil
}