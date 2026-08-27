package businessservice

import (
	"crypto/rand"
	"encoding/hex"
	"strconv"
	"sync/atomic"
	"time"
)

var fallbackIDSequence atomic.Uint64

func newEventID() string {
	var value [16]byte
	if _, err := rand.Read(value[:]); err != nil {
		return fallbackEventID()
	}
	value[6] = (value[6] & 0x0f) | 0x40
	value[8] = (value[8] & 0x3f) | 0x80

	var encoded [36]byte
	hex.Encode(encoded[0:8], value[0:4])
	encoded[8] = '-'
	hex.Encode(encoded[9:13], value[4:6])
	encoded[13] = '-'
	hex.Encode(encoded[14:18], value[6:8])
	encoded[18] = '-'
	hex.Encode(encoded[19:23], value[8:10])
	encoded[23] = '-'
	hex.Encode(encoded[24:36], value[10:16])
	return string(encoded[:])
}

func fallbackEventID() string {
	// crypto/rand 失败极少发生；时间戳加进程内序号仍可避免本进程内碰撞。
	return "event-" + strconv.FormatInt(time.Now().UnixNano(), 36) + "-" +
		strconv.FormatUint(fallbackIDSequence.Add(1), 36)
}
