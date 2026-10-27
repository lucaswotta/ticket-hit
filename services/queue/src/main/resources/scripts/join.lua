-- KEYS[1] meta, KEYS[2] waiting, KEYS[3] admitted
-- ARGV[1] customerId, ARGV[2] now (ms), ARGV[3] nota aleatória em [0, 1)
-- Devolve -1 se a fila não existe, 2 se já foi liberado, 1 se entrou, 0 se já estava.
local opensAt = redis.call('HGET', KEYS[1], 'opensAt')
if not opensAt then
    return -1
end
if redis.call('HEXISTS', KEYS[3], ARGV[1]) == 1 then
    return 2
end
-- Antes da abertura, a nota aleatória é o sorteio. Depois, a nota passa de 1
-- e cresce com o tempo: quem chega depois fica atrás do sorteio, por ordem.
local score = ARGV[3]
if tonumber(ARGV[2]) >= tonumber(opensAt) then
    score = 1 + (tonumber(ARGV[2]) - tonumber(opensAt))
end
-- NX: quem já está na fila mantém o lugar.
local added = redis.call('ZADD', KEYS[2], 'NX', score, ARGV[1])
redis.call('PEXPIREAT', KEYS[2], redis.call('HGET', KEYS[1], 'expiresAt'))
return added
