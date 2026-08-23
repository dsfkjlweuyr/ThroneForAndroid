# libcore direct dependency records

## `github.com/Mahdi-zarei/speedtest-go`

- Version: `v1.7.13-0.20260107171856-79c565dfd83a`
- Commit: `79c565dfd83a54d48dc7275d8766320ef8b9ddb2`
- Source: <https://github.com/Mahdi-zarei/speedtest-go>
- Upstream: <https://github.com/showwin/speedtest-go>
- License: MIT, copyright (c) 2015 ITO Shogo
- License source: <https://github.com/Mahdi-zarei/speedtest-go/blob/79c565dfd83a54d48dc7275d8766320ef8b9ddb2/LICENSE>
- Go checksum database record: <https://sum.golang.org/lookup/github.com/!mahdi-zarei/speedtest-go@v1.7.13-0.20260107171856-79c565dfd83a>
- Module checksum: `h1:11VlAp2Xf/V+W4xXYtWwMAkkm2oBcPRB6oZrSxVoFug=`
- Module file checksum: `h1:b1H+UBFUnLKH1YquN2xao8d1hokcnDFFhEKDARTzddM=`

The version matches the speed-test dependency pinned by Throne commit
`2e7182b9ea99947a409fee30f74df83752ab763c`. CI runs `go mod tidy` before
gomobile binding, so the public checksum database verifies downloaded module
content while the generated `go.sum` remains an intentionally ignored build
artifact.
