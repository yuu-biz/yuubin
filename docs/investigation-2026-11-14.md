# 自前配布アプリの調査と進め方（2026-11-14）

Folder Player Fork、Folirel、Hirari の 3 本を自分で配布し更新するアプリを作る前の調査結果です。コードはまだ書いていません。各項目に、根拠の種類を付けています。「公式引用」は公式ページの原文をこの調査で引用確認したもの、「調査報告」は調査エージェントが公式ページやソースを読んで報告したもの、「実測」はこの調査でコマンドを実行して確かめたもの、「推測」は裏付けのない見込みです。

## 1. 結論

1. 前提は「Folirel と Hirari も最終的に公開リポジトリにする」です（2026-11-14 の利用者の説明）。配る相手は不特定の人になります。この前提なら、配布アプリを自作する意味があります。受け取る人が 1 本入れるだけで済み、証明書の検証を自分で持てるためです。自分の端末だけが対象なら Obtainium で足りますが、今回はそれに当たりません。
2. 配布用の別リポジトリは要りません。各アプリのリポジトリに小さな `app-release.json` を置き、配布アプリがそれを読みます。APK は各リポジトリの GitHub Releases のままです。
3. Android の開発者確認は、直接配布の APK にはまだ適用されていません。全世界への適用は 2027 年の予定です。公開で配るので、アカウントはフル配布（25 ドル、身分証と住所証明）になります。登録は 2027 年に入る前に済ませれば足ります。

## 2. 現状

| アプリ | パッケージ名 | リポジトリ | 最新 | min / target SDK | 署名証明書 SHA-256 |
|---|---|---|---|---|---|
| Folder Player Fork | `com.wing.folderplayer.fork` | `yuu-biz/Folder-Player` PUBLIC（fork） | v1.10.2 | 26 / 34 | `bd7e99204e5596526c83407164e543ab29f7ce77add916cab476303436da3c1a`（実測。v1.10.2 の APK に `apksigner verify --print-certs`） |
| Folirel | `io.github.yuubiz.folirel` | `yuu-biz/folirel` PRIVATE（公開予定） | v1.0.0 | 28 / 37 | `c740ae3b5a57bf46be12edd7894b6a017095b95a3f7217e2cc4b9402cc9b00b1`（`docs/releasing.md`） |
| Hirari | `io.github.yuubiz.hirari` | `yuu-biz/hirari` PRIVATE（公開予定） | v1.0.3 | 28 / 37 | `1116351eafe09b6f99bff277b959ec1700e12aed7bd1bfd463c534ba2d574441`（`docs/releasing.md`） |

- 3 本とも GitHub Releases に APK を置いています。ファイル名の規則は 3 本で違います。
- Folder Player のリリースには APK のほかに `ffmpeg-7.1.5.tar.xz` と `SHA256SUMS.txt` が付いています。tarball は LGPL の FFmpeg のソース提供のためで、リリースノートに説明があります。APK はこのリリースに置いたままにします。
- PRIVATE のリリース資産は匿名で取得できません（実測。Folirel v1.0.0 の APK の URL に認証なしで 404）。公開に切り替えるまで、配布アプリは Folirel と Hirari を取得できません。
- リリースのメタデータに `versionCode` はありません。タグには `-rc.N` や `-debug.N` が付くものがあります。
- 過去のデバッグ署名版は、リリース署名版で上書きできません。

## 3. Morphe の配布方式（調査報告。ソースを `gh api` で読んだもの）

Morphe Manager はパッチャーです。他人のアプリの APK を配る仕組みではありません。参考になるのは Manager 自身の自己更新の部分です。

- 初回の入手は `morphe.software` の静的サイトからです。リンク先は GitHub Releases の APK です。
- 更新の検知は、リポジトリの `main` ブランチに置いた `app-release.json` を読むだけです。失敗したら GitHub Releases API に切り替えます。JSON の中身は `version`、`download_url`、`created_at`、`description` です。
- CI は、APK がダウンロードできる状態になったことを確かめてから JSON をコミットします。順序が逆だと、まだ落とせない版を端末に知らせてしまうためです。
- インストールは `PackageInstaller` のセッションです。`setRequireUserAction(USER_ACTION_NOT_REQUIRED)` で確認なしの更新を試み、端末が拒んだら確認ありでやり直します。Shizuku と root の経路も持っています。
- 自己更新の APK のハッシュも署名証明書も、アプリ内では検証していません。同じ署名でないと上書きできないという Android の規則に頼っています。ここは真似せず、検証を足します。
- 開発者確認への対応は、リポジトリ内に見つかりませんでした。

## 4. Android の開発者確認

出典は <https://developer.android.com/developer-verification/guides> と、その下の `/faq`、`/limited-distribution`、`/full-distribution`、`/android-developer-console` です。

### 4.1 適用の範囲と時期（公式引用）

- 「Starting September 30, 2026, these new developer verification protections go live for users in Brazil, Indonesia, Singapore, and Thailand.」
- この段階で検査されるのは、Google Play、HONOR、OPPO、Galaxy Store、Palm Store、V-Appstore、GetApps の 7 ストアからのインストールだけです。
- 「If you distribute your app through other stores, or if users sideload your app directly, these new verification requirements won't apply to your app yet.」
- 「The verification capability will soon be expanded to all third-party Android app stores.」配布アプリ自身もインストーラなので、この拡大の対象になります。
- 「we will expand these protections globally for all apps on certified Android devices in 2027.」日本の個別の日付は公式ページにありません。

### 4.2 アカウントの種類（公式引用。表の下の箇条書きは調査報告）

| | 限定配布（Limited Distribution） | フル配布（Full Distribution） |
|---|---|---|
| 費用 | 無料 | 25 ドル |
| 本人確認 | 政府発行の身分証は不要。氏名と住所は支払いプロファイルで管理 | 「Individuals must submit a government-issued photo ID and proof of address document」 |
| 配れる範囲 | 承認した端末だけ。上限はページによって 20 台と 50 台で食い違う | 制限なし |
| 向く用途 | 自分と家族の端末 | 不特定の人への公開 |

- 限定配布では、承認していない端末は登録したパッケージ名のアプリをインストールも更新もできません。公開で配る今回の前提には合いません。
- 作成後の種類変更は記載が食い違います。ヘルプは「変更できない」、FAQ は「限定からフルへ移行できる」としています。
- 個人の本名や住所が公開されるかどうかは、公式ページに記載が見つかりませんでした。
- 必要な書類は地域によって変わると公式ページにあります。日本で使える書類の一覧は確認できていません。

### 4.3 登録の手順

1. Android Developer Console でアカウントを作り、本人確認を終える。終わるまでパッケージ登録はできません（調査報告）。
2. パッケージ名を登録し、署名鍵の証明書 SHA-256 を入力する。状態は「In review」になります（公式引用）。
3. 既に配布しているパッケージは所有の証明が要ります。「you must sign an APK with your private key and upload it. The Android Developer Console provides a snippet to add to the APK's asset folder」（公式引用）。3 本とも、この証明用のビルドを 1 回作ります。
4. 新しいパッケージ名（配布アプリ自身）は、指紋の登録だけで済みます（調査報告）。

鍵を失うと登録できません（調査報告）。配布アプリ用の鍵を新しく作るときも、既存 3 本と同じ手順でバックアップします。

### 4.4 パッケージ名の競合（調査報告、一部推測）

同じ名前を複数人が主張した場合の順序は次のとおりです。既知のインストール数の過半を持つ鍵が優先です。それがなければ、50 以上のインストールを持つ鍵が登録できます。それもなければ先着です。`com.wing.folderplayer.fork` は上流の `com.wing.folderplayer` とは別の名前なので、新規の名前として登録できる見込みです（推測。名前空間が似ている場合の規則は公式にありません）。

### 4.5 未登録アプリの入れ方（公式引用）

- ADB でのインストールは従来どおりです。「The waiting period does not apply to ADB installs.」
- 「advanced flow」は 1 回だけの設定です。開発者モードを有効にし、再起動して再認証し、1 日待ってから生体認証か PIN で確認します。有効期間は 7 日か無期限を選べます。

### 4.6 配布アプリ自身の扱い（調査報告）

- 配布アプリは普通のパッケージの 1 つです。登録するパッケージは合計 4 本になります。
- 「Registered App Stores」という別制度は、ワンタップのインストールとバックグラウンド更新を与えます。ただし組織アカウント、年会費、審査が必要で、個人は対象外です。
- API 36 より上を target にしたインストーラは、開発者確認で失敗したときに `STATUS_FAILURE_ABORTED` と理由コードを受け取ります（`PackageInstaller` リファレンス）。2027 年に向けて、この失敗を画面に出す処理が要ります。

## 5. 方式の比較

| | 自作の配布アプリ | Obtainium | 自前の F-Droid リポジトリ | 各アプリに自己更新を内蔵 |
|---|---|---|---|---|
| 作る量 | 多い（一覧、ダウンロード、セッション、検証） | ほぼゼロ | 中（fdroidserver、鍵、CI） | 中（同じ処理を 3 か所） |
| 受け取る人の手間 | 1 本入れるだけ | 設定の取り込みが要る | リポジトリ URL の追加が要る | なし |
| 確認なしの更新 | できる（6 章の条件） | できる（同じ条件） | できる（F-Droid 1.19 以降） | できる（自己更新は条件に含まれる） |
| 証明書のピン留め | 自分で入れられる | なし | index 署名で代替 | 自分で入れられる |
| 保守 | 最も重い。OS の変更に追随する | 軽い | 軽い〜中 | 中 |

`app-release.json` と公開の Releases があれば、Obtainium からも更新できます。配布アプリが未完成の間の代わりになります。

## 6. インストールの仕組み（調査報告。`PackageInstaller.SessionParams` リファレンス）

確認ダイアログなしで更新できるのは、次をすべて満たすときです。

- 端末が Android 12 以上。
- 配布アプリが `UPDATE_PACKAGES_WITHOUT_USER_ACTION` を宣言し、セッションに `USER_ACTION_NOT_REQUIRED` を指定している。
- 配布アプリが、そのアプリを前回インストールしたアプリである。または自分自身を更新している。
- 更新されるアプリの targetSdk が OS ごとの下限以上である。Android 16 は 34 以上、Android 17 は 35 以上。

ここから分かること。

- 初回インストールは必ず確認が出ます。
- ブラウザで入れた既存の 3 本は、配布アプリから 1 回手動で更新すると、次から確認なしになる見込みです（推測。F-Droid の同じ挙動の説明に基づく）。
- Folder Player Fork は targetSdk 34 です。Android 17 の端末では下限の 35 に届かず、毎回確認が出ます。targetSdk を上げる作業が別に要ります。
- 配布アプリは自分自身も確認なしで更新できます。

## 7. 推奨する構成

```
各アプリのリポジトリ（公開）
  ├ Releases: APK（今のまま）
  └ app-release.json（main ブランチ。APK が落とせることを確かめてからコミット）
        ↓ アプリごとに 1 回の GET（raw.githubusercontent.com）
配布アプリ（Android、このリポジトリ）
  ├ 内蔵の一覧: パッケージ名、app-release.json の URL、署名証明書 SHA-256
  └ 一覧 → ダウンロード → SHA-256 と証明書を検証 → PackageInstaller セッション
```

`app-release.json` は次の形にします。

```json
{
  "packageName": "io.github.yuubiz.hirari",
  "versionCode": 14,
  "versionName": "1.0.3",
  "apkUrl": "https://github.com/yuu-biz/hirari/releases/download/v1.0.3/Hirari-1.0.3.apk",
  "apkSha256": "…",
  "minSdk": 28,
  "changelog": "…"
}
```

- 新旧の判定は `versionCode` の整数比較だけにします。タグ名の `-rc` を解釈する処理が不要になります。
- `apkUrl` を明示するので、リリースに tarball が混ざっていても選び間違えません。
- 署名証明書の SHA-256 は配布アプリに埋め込み、ダウンロードした APK の実際の証明書と照合します。JSON が書き換えられても、別の鍵の APK は入りません。扱うアプリを増やすときは配布アプリの更新が要ります。配布アプリは自己更新できるので、これで足ります。
- リポジトリをまたぐ書き込みがないので、CI に追加のトークンは要りません。
- GitHub API は使いません。認証なしの上限は IP ごとに毎時 60 回ですが、リリース資産のダウンロードはこの上限を減らしません（調査報告の実測）。
- 配布アプリ自身も同じ `app-release.json` を自分のリポジトリに置き、同じ経路で自己更新します。

## 8. 進め方

| 段階 | 内容 | 終わりの確認 | 担当モデルの目安 |
|---|---|---|---|
| 0 | 決める。配布アプリの名前とパッケージ名、新しい署名鍵の作成 | 9 章に答えが付く | 利用者 |
| 1 | `app-release.json` のスキーマを決め、Folder Player のリリース手順に生成とコミットを足す。Folirel と Hirari は公開の準備と一緒に足す | 端末のブラウザで JSON と APK が落ちる | 設計は Fable、CI と文書は Sonnet high |
| 2 | 配布アプリの最小版。一覧、インストール済みの版との比較、ダウンロード、検証、確認ありのインストール。公開済みの Folder Player で試す | 実機で新規に入れられる。証明書が違う APK を拒む | 検証とセッションの状態管理は Opus high、画面は Sonnet high |
| 3 | 確認なしの更新、定期チェック、自己更新 | 実機で 2 回目以降の更新に確認が出ない | Opus high |
| 4 | 開発者確認の登録。フル配布アカウントの作成、4 パッケージの登録、既存 3 本の証明用ビルド | Console で 4 本が「Registered」になる | 手順書は Sonnet high、操作は利用者 |
| 5 | 2027 年の全世界適用への備え。確認失敗の理由コードの表示、Folder Player の targetSdk 引き上げ | エミュレータで失敗時の表示を確認 | Opus high |

段階 4 は段階 1〜3 と独立です。全世界への適用は 2027 年なので、段階 2 の後でも間に合います。Folirel と Hirari の公開の切り替えは利用者が行います。このリポジトリの作業からは切り替えません。

## 9. 決めてほしいこと

1. **配布アプリの名前とパッケージ名。** 例 `io.github.yuubiz.<名前>`。
2. **配布アプリ用の署名鍵を新しく作ってよいか。** 既存 3 本と同じく、リポジトリの外に置き、NAS と Google Drive にバックアップします。
3. **フル配布アカウントを作る時期。** 身分証と住所証明を Google に出します。本名や住所が公開されるかどうかは未確認なので、作る前にその点を公式ページで確かめます。

1 と 2 の答えがなくても、段階 1 の設計と段階 2 のデバッグ署名での開発は進められます。

## 10. 未確認の点

- 個人のフル配布アカウントで本名や住所が公開されるか。
- 日本で受け付けられる身分証と住所証明の種類、審査にかかる日数。
- 既存のインストールを配布アプリから 1 回更新すれば、次から確認なしになるか。段階 3 の実機で確かめます。
- 限定配布の端末上限（20 台か 50 台か）。今回は使わない見込みです。
