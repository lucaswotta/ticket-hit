-- KEYS[1] meta, KEYS[2] waiting, KEYS[3] admitted | ARGV[1] now (ms)
-- Token bucket: libera quantos o tempo desde a última liberação permite. Como o
-- script é atômico, a taxa vale com qualquer número de instâncias chamando.
local opensAt = tonumber(redis.call('HGET', KEYS[1], 'opensAt'))
local now = tonumber(ARGV[1])
if not opensAt or now < opensAt then
    return {}
end
local rate = tonumber(redis.call('HGET', KEYS[1], 'admissionsPerSecond'))
local last = tonumber(redis.call('HGET', KEYS[1], 'lastAdmissionAt') or opensAt)
local due = math.floor((now - last) * rate / 1000)
if due <= 0 then
    return {}
end
local popped = redis.call('ZPOPMIN', KEYS[2], due)
local admitted = {}
for i = 1, #popped, 2 do
    redis.call('HSET', KEYS[3], popped[i], ARGV[1])
    admitted[#admitted + 1] = popped[i]
end
-- Fila vazia não acumula crédito. Arredondar para cima nunca passa da taxa.
if #admitted < due then
    last = now
else
    last = last + math.ceil(due * 1000 / rate)
end
redis.call('HSET', KEYS[1], 'lastAdmissionAt', last)
if #admitted > 0 then
    redis.call('PEXPIREAT', KEYS[3], redis.call('HGET', KEYS[1], 'expiresAt'))
end
return admitted
