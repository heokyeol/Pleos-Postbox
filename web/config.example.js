// 이 파일을 config.js로 복사하고 Supabase 값을 넣는다. (config.js는 커밋하지 않는다)
// anon key는 브라우저에 공개되는 키다. supabase/schema.sql의 RLS가 반드시 적용돼 있어야 한다.
export default {
  supabaseUrl: "https://YOUR-PROJECT.supabase.co",
  supabaseAnonKey: "YOUR-ANON-KEY",
};
