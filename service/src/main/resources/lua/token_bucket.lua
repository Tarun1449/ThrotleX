-- Keys
local rate_limit_key = KEYS[1]

-- Args
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- Read current state from Redis Hash
local current_state = redis.call('HMGET', rate_limit_key, 'tokens', 'last_refill')
local tokens = current_state[1]
local last_refill = current_state[2]

if not tokens or not last_refill then
    -- First time user is seen, initialize bucket
    tokens = capacity
    last_refill = now
else
    tokens = tonumber(tokens)
    last_refill = tonumber(last_refill)

    -- Calculate tokens to add based on time elapsed
    local seconds_passed = math.max(0, now - last_refill)
    local new_tokens = seconds_passed * refill_rate

    if new_tokens > 0 then
        tokens = math.min(capacity, tokens + new_tokens)
        last_refill = now
    end
end

-- Check if we can consume 1 token
if tokens > 0 then
    -- Consume 1 token
    tokens = tokens - 1
    
    -- Save back to Redis
    redis.call('HMSET', rate_limit_key, 'tokens', tokens, 'last_refill', last_refill)
    
    -- Set an expiry so unused buckets are eventually deleted (e.g. 1 hour)
    redis.call('EXPIRE', rate_limit_key, 3600)
    
    return 1 -- Allowed
else
    return 0 -- Rate limit exceeded
end
