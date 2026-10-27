-- KEYS[1] meta | ARGV[1] opensAt (ms), ARGV[2] admissionsPerSecond, ARGV[3] expiresAt (ms)
if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end
redis.call('HSET', KEYS[1], 'opensAt', ARGV[1], 'admissionsPerSecond', ARGV[2], 'expiresAt', ARGV[3])
redis.call('PEXPIREAT', KEYS[1], ARGV[3])
return 1
